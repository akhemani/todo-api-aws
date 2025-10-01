# AWS Architecture for Dockerized Spring Boot Application with MySQL

This architecture is for deploying a Dockerized Spring Boot application with a MySQL database on AWS, using ECS (Fargate), Application Load Balancer (ALB), RDS, and supporting services like ECR, Secrets Manager, IAM, and GitHub Actions for CI/CD. It includes a detailed request flow, explaining how user requests from the internet traverse AWS services and how outbound traffic (e.g., API calls or downloads) is handled.

## Components Used
- **VPC**: Isolates resources with a defined IP range and subnets (public and private).
- **Internet Gateway (IGW)**: Enables internet access for public subnets, allowing inbound and outbound traffic.
- **Route Tables**: Define rules for routing *outbound* traffic from subnets to destinations like the internet, AWS services, or within the VPC.
- **NAT Gateway**: Allows private subnets to initiate outbound internet traffic while blocking inbound connections.
- **Security Groups**: Virtual firewalls associated with AWS resources (e.g., ALB, ECS tasks, RDS), controlling who can connect to the resource (inbound) and whom the resource can connect to (outbound).
- **Application Load Balancer (ALB)**: Distributes incoming HTTP/HTTPS traffic across ECS tasks.
- **Target Group (TG)**: Routes ALB traffic to specific ECS task IPs, with health checks to ensure only healthy tasks receive traffic.
- **ECS Cluster**: A logical grouping for ECS services and tasks.
- **ECS Task Definition**: Specifies container images, resources, networking, and secrets.
- **ECS Service**: Manages task replicas and ALB integration.
- **ECS Task**: Running container instances based on the Task Definition.
- **RDS (MySQL)**: Managed MySQL database for persistent storage.
- **IAM**: Roles and policies for secure access to AWS services.
- **Secrets Manager**: Stores sensitive data like RDS credentials.
- **ECR**: Hosts Docker images for the application.
- **GitHub Actions**: Automates CI/CD to build and push images to ECR.

## Architecture Setup
### 1. Build and Push Dockerized Application
- **Description**: Develop a Spring Boot application with MySQL, packaged as a Docker container, and automate image building/pushing to ECR via GitHub Actions.
- **Details**:
  - Create a `Dockerfile` (e.g., using `openjdk:17`, copy JAR, expose port 8080).
  - Store code in a GitHub repository.
  - Create a `.github/workflows/deploy.yml` workflow to:
    - Build the Docker image with a versioned tag (e.g., `v1.0.0`).
    - Push to an ECR repository with scan-on-push enabled for vulnerability scanning.
  - Use OpenID Connect (OIDC) for secure authentication:
    - Store AWS Account ID, ECR repository name, region, and IAM Role ARN in GitHub Secrets.
    - Configure `aws-actions/configure-aws-credentials` for temporary credentials via OIDC.

### 2. VPC and Networking Setup
- **Description**: Configure a VPC with subnets, route tables for outbound traffic, NAT Gateways for private subnet internet access, and Security Groups for resource-level access control.
- **Details**:
  - Create a **VPC** with CIDR `10.0.0.0/16`.
  - Create **4 subnets** across 2 AZs:
    - Public: `10.0.1.0/24` (us-east-1a), `10.0.2.0/24` (us-east-1b).
    - Private: `10.0.3.0/24` (us-east-1a), `10.0.4.0/24` (us-east-1b).
  - Attach an **Internet Gateway (IGW)** to the VPC for public subnet internet connectivity.
  - Create **Route Tables** for *outbound* traffic routing:
    - **Public Route Table** (associated with public subnets):
      - `10.0.0.0/16` → `local` (VPC-internal traffic).
      - `0.0.0.0/0` → IGW (internet-bound traffic, e.g., ALB responses).
    - **Private Route Tables** (one per AZ or shared, associated with private subnets):
      - `10.0.0.0/16` → `local` (VPC-internal traffic).
      - `0.0.0.0/0` → NAT Gateway (outbound to internet/AWS services).
  - Deploy **NAT Gateways** in each public subnet (us-east-1a, us-east-1b) for private subnet outbound access, ensuring HA.
  - Create **Security Groups** (associated with resources, not subnets):
    - **ALB-SG**: Inbound HTTP/80, HTTPS/443 from `0.0.0.0/0`; outbound TCP/8080 to ECS-SG.
    - **ECS-SG**: Inbound TCP/8080 from ALB-SG; outbound TCP/3306 to RDS-SG, `0.0.0.0/0` for external APIs.
    - **RDS-SG**: Inbound TCP/3306 from ECS-SG; outbound `0.0.0.0/0` for updates.

### 3. Application Load Balancer and Target Group
- **Description**: Deploy an ALB to distribute traffic to ECS tasks via a Target Group.
- **Details**:
  - Create an **ALB** in public subnets (us-east-1a, us-east-1b), assign **ALB-SG**.
  - Configure listeners: HTTP/80 (redirect to HTTPS/443), HTTPS/443 with ACM certificate.
  - Create a **Target Group** (type: IP):
    - Protocol/port: HTTP/8080.
    - Health check: HTTP GET `/health` on 8080.
    - Targets: ECS task IPs (dynamically registered).

### 4. MySQL RDS Database
- **Description**: Deploy a MySQL RDS instance in private subnets with credentials in Secrets Manager.
- **Details**:
  - Create an **RDS instance** (MySQL) in a DB subnet group (private subnets, multi-AZ).
  - Assign **RDS-SG**, disable public access, enable Secrets Manager for credentials.
  - Set DB identifier (e.g., `my-spring-db`).

### 5. Secrets Manager
- **Description**: Use Secrets Manager for RDS credentials.
- **Details**:
  - Use the auto-generated RDS secret (JSON, with ARN).
  - No manual plaintext secrets.

### 6. IAM Roles
- **Description**: Create roles for GitHub Actions and ECS task execution.
- **Details**:
  - **GitHub Actions Role**: OIDC trust, `AmazonEC2ContainerRegistryPowerUser` policy.
  - **ECS Task Execution Role**: Policies for ECR pull, CloudWatch Logs, Secrets Manager read.
  - Optional **ECS Task Role** for additional AWS service access.

### 7. ECS Task Definition
- **Description**: Define the container, resources, and secrets for ECS tasks.
- **Details**:
  - Create a **Task Definition** (Fargate, Linux/x86_64, 0.5 vCPU, 1GB RAM).
  - Container: ECR image, port 8080, AWS Logs driver.
  - Env vars: `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME` (secret), `SPRING_DATASOURCE_PASSWORD` (secret), `RDS_ENDPOINT`.
  - Health check: HTTP `/health`.

### 8. ECS Cluster and Service
- **Description**: Create a cluster and service to manage tasks and ALB integration.
- **Details**:
  - Create an **ECS Cluster** (Fargate).
  - Create an **ECS Service**: Use Task Definition, 2 replicas, private subnets, ECS-SG, link to ALB/Target Group.

# Request Flow

## User Request to ALB
- A user sends an HTTP/HTTPS request to the ALB’s public DNS name (e.g., `myapp.<region>.elb.amazonaws.com`), resolved via Route 53 or another DNS provider to the ALB’s public IP in the VPC’s public subnets.
- The Internet Gateway (IGW) enables the ALB to receive internet traffic by connecting public subnets to the internet. The request reaches the ALB directly via its public IP, not through explicit IGW routing.

## ALB Security Group Check
- The ALB’s ALB-SG (associated with the ALB resource) checks inbound rules, allowing HTTP/80 or HTTPS/443 from `0.0.0.0/0` (or restricted IPs). If permitted, the request proceeds.

## ALB to Target Group
- The ALB’s listener (HTTP/80 or HTTPS/443) forwards the request to the Target Group (type: IP, protocol: HTTP/8080).
- The Target Group maintains a list of healthy ECS task IPs, determined by health checks (e.g., HTTP GET `/health` on port 8080, with thresholds like 3 successes, 2 failures).
- The Target Group selects a healthy task IP and forwards the request.

## Target Group to ECS Task
- The Target Group routes the request to a healthy ECS task running in a private subnet with an Elastic Network Interface (ENI).
- Each ECS task (managed by the ECS Service in `awsvpc` networking mode) has an ENI created by Fargate, assigned a private IP in the private subnet and associated with the ECS-SG (not ALB-SG or RDS-SG).
- The ECS Service configuration specifies the ECS-SG, which is attached to the ENI of each task, not the service itself.
- The ECS-SG allows inbound TCP/8080 from ALB-SG, enabling the ALB/Target Group to reach the task’s Spring Boot application on port 8080.
- The ECS Service ensures task IPs are registered/deregistered in the Target Group based on health check status.

## ECS Task Processing
- The ECS task runs the Spring Boot application (based on the Task Definition).
- At task startup (not per request):
  - The Task Execution Role pulls the Docker image from ECR (using `AmazonECSTaskExecutionRolePolicy`).
  - The role retrieves RDS credentials from Secrets Manager (via ARN, using `secretsmanager:GetSecretValue`) and injects them as environment variables (e.g., `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`).
  - The Task Definition provides the RDS endpoint (e.g., `myspring-db.<random>.us-east-1.rds.amazonaws.com`) via an environment variable (e.g., `SPRING_DATASOURCE_URL=jdbc:mysql://${RDS_ENDPOINT}:3306/dbname`).
- The Spring Boot app processes the request and connects to the RDS instance (in private subnets, associated with RDS-SG) on port 3306.
- The ECS-SG allows outbound TCP/3306 to RDS-SG, which permits inbound 3306 from ECS-SG.
- The RDS instance executes the query and returns data to the app.

## Response Path
- The Spring Boot app generates a response, sent from the ECS task (via its ENI) to the Target Group.
- The Target Group forwards the response to the ALB (in public subnets).
- AWS networking is stateful, so the response follows the established connection back through the ALB, requiring no NAT Gateway.
- The ALB-SG allows outbound traffic (e.g., HTTP/80 or HTTPS/443) to the internet.
- The public route table (associated with public subnets) routes `0.0.0.0/0` traffic to the IGW, enabling the ALB to send the response to the user over the internet.
- The private subnet’s route table is not involved, as the response flows from the task to the ALB (within the VPC) and then to the internet.

## Outbound Internet Requests from Private Subnet Resources (e.g., API Calls or Downloads)
- If the Spring Boot app (or RDS) initiates an outbound request to the internet (e.g., calling an external API or downloading a resource), the request originates from the ECS task (or RDS) in a private subnet.
- The private route table (associated with the private subnet) routes:
  - `10.0.0.0/16` → `local` (for VPC-internal traffic, e.g., task to RDS).
  - `0.0.0.0/0` → NAT Gateway (in a public subnet).
- The NAT Gateway forwards the request to the IGW (via the public subnet’s public route table, which routes `0.0.0.0/0` to the IGW).
- The IGW sends the request to the internet.
- The response returns via the reverse path: internet → IGW → NAT Gateway → private subnet (task or RDS).
- The ECS-SG (for tasks) or RDS-SG (for RDS) allows outbound traffic to `0.0.0.0/0` (e.g., HTTPS/443 for APIs or other ports as needed).

## Verification
- Confirm tasks are `RUNNING` in the ECS Service’s **Tasks** tab.
- Check **CloudWatch Logs** for container logs.
- Access the app via the ALB DNS (e.g., `https://myapp.<region>.elb.amazonaws.com`).
- Troubleshoot issues:
  - **ECS Events**: Check for task failures.
  - **Security Groups**: Verify ALB-SG → ECS-SG → RDS-SG chain.
  - **IAM**: Ensure Task Execution Role has ECR pull and Secrets Manager permissions.
  - **Route Tables**: Confirm private route tables point to NAT Gateways.

## Notes
- Use **CloudWatch** for metrics (ALB latency, ECS CPU, RDS connections) and alarms.
- Enforce **HTTPS** with ACM and TLS for RDS in production.
- Update **Task Definitions** for new ECR images and redeploy the ECS Service.
- Validate **ENI** assignments in private subnets and correct SG associations (ECS-SG only).
