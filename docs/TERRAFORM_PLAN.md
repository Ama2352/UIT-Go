# Terraform Multi-Cloud Plan (LocalStack / AWS / Azure)

## Objectives and Principles
- Single module interface that works for `cloud_provider = localstack | aws | azure`.
- Keep state isolated per env (`envs/{local,aws-dev,azure-dev,...}`) with the same modules and tfvars.
- Favor serverless/managed for prod, containers for portability, and LocalStack + Docker for daily dev.
- Cost-first defaults: small instance sizes, on-demand only when needed, aggressive autoscaling, and teardown scripts.

## Target Layout
```
infrastructure/terraform/
├── envs/
│   ├── local/            # LocalStack + docker-compose already present
│   ├── aws-dev/
│   ├── azure-dev/
│   └── <more>
├── modules/
│   ├── network/          # VPC/VNet (exists)
│   ├── identity/         # IAM roles / Managed Identity
│   ├── storage/          # S3 / Azure Storage
│   ├── container_runtime/# ECS Fargate / Azure Container Apps
│   ├── database/         # RDS Postgres / Azure PG Flexible / docker Postgres
│   ├── cache/            # ElastiCache / Azure Cache / docker Redis
│   ├── messaging/        # SQS+SNS or AmazonMQ / Azure Service Bus / docker RabbitMQ
│   ├── ingress/          # ALB / Application Gateway (or NLB for GRPC)
│   ├── observability/    # CloudWatch / Log Analytics + App Insights
│   └── secrets/          # Secrets Manager / Key Vault (optional in local)
└── common/locals.tf      # naming, tags, mappings, provider aliases (optional helper)
```

## Roadmap (Ordered)
1) **State & Providers**  
   - Add remote state per cloud: S3+DynamoDB for AWS, Storage Account+container for Azure; keep LocalStack state local.  
   - Standardize provider aliases (`aws`, `aws.localstack`, `azurerm`) and `cloud_provider` variable in each env.  
   - Expected: `terraform init` succeeds for all envs; state buckets/tables created.

2) **Network Module (refine existing)**  
   - Harden current `modules/vpc` to support NAT gateways (AWS) and separate route tables; add Azure NSGs.  
   - Add outputs: cidrs, route table ids, subnet maps for private/public.  
   - Expected: VPC/VNet with subnets and internet egress; module works under three providers.

3) **Identity Module**  
   - AWS: IAM roles for ECS tasks, execution, and least-priv S3/KMS access.  
   - Azure: User-assigned managed identity for Container Apps; role assignments for storage and Key Vault.  
   - Expected: Role ARNs/IDs ready for downstream modules; no secrets hardcoded.

4) **Storage Module**  
   - AWS: S3 bucket with versioning off by default, lifecycle to IA/Glacier for logs.  
   - Azure: Storage Account (Standard_LRS) with blob container.  
   - Local: LocalStack S3 endpoint.  
   - Expected: bucket/container names output; cost controls via lifecycle + block public access.

5) **Container Runtime Module**  
   - AWS: ECS Fargate cluster + services; use `FARGATE_SPOT` where safe; security groups from network module.  
   - Azure: Container Apps Environment + apps; Dapr off unless needed; min replicas 0–1.  
   - Local: docker-compose driven; Terraform only wires network + dummy outputs.  
   - Expected: Service endpoints (ALB/App Gateway) and task definitions/app revisions.

6) **Database Module**  
   - AWS: RDS PostgreSQL (db.t4g.micro) with storage autoscaling; optional read replica flag.  
   - Azure: Flexible Server (B1ms) with auto-stop if non-prod.  
   - Local: docker postgres container via compose; TF exposes connection info only.  
   - Expected: Connection strings in outputs/SSM/Key Vault; backups enabled in cloud envs.

7) **Cache Module**  
   - AWS: ElastiCache Redis (cache.t4g.micro) in private subnets; snapshot retention minimal.  
   - Azure: Azure Cache for Redis Basic/Standard C0/C1.  
   - Local: docker Redis.  
   - Expected: Redis endpoint outputs; security group rules applied.

8) **Messaging Module**  
   - Prefer AmazonMQ (RabbitMQ) vs Azure Service Bus (RabbitMQ-compatible SKU) to align with app; fallback to SQS/SNS if queue/topic is enough.  
   - Local: docker RabbitMQ.  
   - Expected: Broker endpoints and creds stored in secrets module; network rules applied.

9) **Ingress Module**  
   - AWS: ALB with HTTPS (ACM) and WAF optional flag; target groups per service.  
   - Azure: Application Gateway with listener + routing rules.  
   - Local: reuse root docker-compose nginx/kong; Terraform only returns placeholder.  
   - Expected: Public DNS/endpoint outputs.

10) **Observability Module**  
    - AWS: CloudWatch log groups per service, metric filters, optional X-Ray.  
    - Azure: Log Analytics workspace + App Insights.  
    - Local: docker loki/promtail/grafana (optional) with TF returning endpoints.  
    - Expected: Log group/workspace ids; dashboards seeded as code.

11) **Secrets Module (optional but recommended)**  
    - AWS: Secrets Manager for DB/broker creds; rotation off by default.  
    - Azure: Key Vault with access policies for managed identities.  
    - Local: `.env` or docker secrets; Terraform returns no secret data.  
    - Expected: Secret ARNs/IDs and access policies in outputs.

12) **Pipelines**  
    - Add GitHub Actions (or Azure DevOps) to run `terraform fmt/validate/plan` per env; guardrails for cost (`terraform plan` fail on large diffs using `infracost` if available).  
    - Expected: CI plan artifacts; no direct apply in CI for prod without approval.

## Module Patterns (Step-by-Step)
- **Common variables**: `cloud_provider`, `region`, `project_name`, `tags`, `cidr_blocks`, `env`. Keep provider-specific defaults in locals per module.
- **Conditional resources**: prefer `for_each` with maps over `count` to keep indexes stable. Example:
  ```hcl
  resource "aws_vpc" "this" {
    for_each = var.cloud_provider == "aws" || var.cloud_provider == "localstack" ? { main = 1 } : {}
    cidr_block = var.cidr_block
  }
  ```
- **Provider routing**: pass aliases into modules.
  ```hcl
  module "network" {
    source         = "../../modules/network"
    cloud_provider = var.cloud_provider
    providers = {
      aws    = aws
      azurerm = azurerm
    }
  }
  ```
- **Outputs**: expose connection endpoints, security group ids, and maps keyed by logical name to avoid brittle indices.
- **State isolation**: `backend.tf` per env pointing to the right bucket/container; keep LocalStack backend local to avoid accidental cloud writes.
- **Composition**: env `main.tf` wires modules together; no resource definitions in env folders beyond module calls and data lookups.

## Concrete Steps and Expected Results
- **S0 – Backend setup (per env)**  
  - Create S3 bucket (`uitgo-tfstate-<env>`) + DynamoDB table for locks; for Azure create Storage Account+container.  
  - Update `envs/*/backend.tf`; run `terraform init -migrate-state` as needed.  
  - *Output*: State bucket/container and lock table names; init success.

- **S1 – Network**  
  - Extend `modules/vpc`: add NAT gateways (flag `enable_nat`), private route tables, NSGs for Azure.  
  - Add `outputs.tf` with subnet maps `{public = [...], private = [...]}` and route table ids.  
  - *Output*: VPC/VNet ids, subnet ids, IGW/App Gateway readiness for ingress module.

- **S2 – Identity**  
  - New module `modules/identity`: IAM roles/instance profiles; Managed Identity with role assignments.  
  - *Output*: Role ARNs/IDs and principals for services to attach.

- **S3 – Storage**  
  - New module `modules/storage`: S3/Storage Account + lifecycle rules (logs to IA after 30d).  
  - *Output*: bucket/container names, kms/encryption status.

- **S4 – Container Runtime**  
  - New module `modules/container_runtime`: ECS cluster, task defs, services with ALB target groups; ACA environment and apps.  
  - *Output*: Service URLs, cluster/aca env ids, task role ARNs.

- **S5 – Data**  
  - New module `modules/database`: RDS/PG Flexible; optional `deploy=false` for local using docker.  
  - *Output*: connection endpoints, secret ids, parameter group names.

- **S6 – Cache & Messaging**  
  - `modules/cache` and `modules/messaging`; enable TLS where supported; place in private subnets.  
  - *Output*: Redis endpoints, broker endpoints, security group ids.

- **S7 – Ingress**  
  - `modules/ingress`: ALB/App Gateway; ACM/Key Vault cert wiring; WAF optional.  
  - *Output*: public DNS/hostname, listener ARNs, rule ids.

- **S8 – Observability**  
  - `modules/observability`: CloudWatch log groups, metric filters, dashboards; Azure workspace + App Insights.  
  - *Output*: log group names, workspace ids, dashboard urls.

- **S9 – Secrets**  
  - `modules/secrets`: wrapper to create secrets and grant roles/identities from identity module.  
  - *Output*: secret ARNs/URIs, access policy ids.

## Code Suggestions
- Add a `common/locals.tf` (or reuse root) to centralize naming:
  ```hcl
  locals {
    name     = "${var.project_name}-${var.env}"
    tags     = merge(var.tags, { env = var.env, project = var.project_name })
    region   = var.cloud_provider == "azure" ? var.azure_region : var.aws_region
  }
  ```
- Example env wiring:
  ```hcl
  module "storage" {
    source         = "../../modules/storage"
    cloud_provider = var.cloud_provider
    name           = local.name
    tags           = local.tags
  }
  ```
- For LocalStack, set provider endpoints and disable features not supported (e.g., RDS snapshots); gate with `deploy_cloud_resources = var.cloud_provider != "localstack"`.

## Cost-Saving Strategies
- Default to smallest viable SKUs (t4g.micro / B1ms) with autoscale rules; use `FARGATE_SPOT` and ACA min replicas 0 for non-prod.
- Turn on lifecycle policies (S3/Storage) and short log retention (7–14d non-prod, 30d prod) with compression.
- NAT gateways: flag to disable in dev; use single shared NAT in small envs.
- Prefer serverless for spiky workloads (ACA consumption, Lambda for cron) instead of always-on services.
- Shut down non-prod DBs at night (Azure auto-stop; AWS use snapshot + destroy toggle).
- Use LocalStack/docker for daily development of DB/cache/messaging; only enable cloud for integration/staging.
- Centralize secrets once; avoid duplicating credentials across modules.

## What to Simulate vs Deploy
- **Simulated (local/localstack)**: Postgres, Redis, RabbitMQ via docker-compose; S3; simple ALB substitute via Kong/NGINX already in compose; secrets via .env.
- **Cloud-required**: Network (VPC/VNet), identity (IAM/Managed Identity), container runtime (ECS/ACA) for staging/prod, DNS/Certs, observability sinks.
- **Optional in lower envs**: WAF, multi-AZ DB, cross-region replication, KMS/HSM—enable only for perf/load or prod.

## Design Notes
- Keep module interfaces cloud-agnostic; use provider-specific resources behind conditionals and map outputs to neutral names (`storage_endpoint`, `queue_endpoint`).
- Avoid index-based `count` where Azure/AWS divergence can drift; prefer named maps to keep resource addresses stable.
- Inputs/outputs should be consistent across providers so app manifests (ECS task defs / ACA) consume the same variable names.
- Document any provider gaps (e.g., features unsupported by LocalStack) in each module README and expose `deploy=false` flags.

## Expected End State
- Running `terraform plan` in `envs/local` provisions LocalStack wiring and emits placeholder outputs for dockerized services.  
- Switching `cloud_provider` to `aws` or `azure` in env tfvars reuses the same modules and produces equivalent infrastructure graphs with minimal drift.  
- CI validates formatting and plans; cost is constrained by defaults and toggles; production-ready path is clear with minimal rework.
