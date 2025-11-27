# =============================================================================
# Network Module - AWS / LocalStack Resources
# =============================================================================
# These resources are created when cloud_provider = "aws" or "localstack"
# =============================================================================

# -----------------------------------------------------------------------------
# VPC
# -----------------------------------------------------------------------------
resource "aws_vpc" "this" {
  count = local.is_aws ? 1 : 0

  cidr_block           = var.cidr_block
  enable_dns_support   = true
  enable_dns_hostnames = true

  tags = {
    Name    = "${var.project_name}-vpc"
    Project = var.project_name
  }
}

# -----------------------------------------------------------------------------
# Internet Gateway
# -----------------------------------------------------------------------------
resource "aws_internet_gateway" "this" {
  count = local.is_aws ? 1 : 0

  vpc_id = aws_vpc.this[0].id

  tags = {
    Name    = "${var.project_name}-igw"
    Project = var.project_name
  }
}

# -----------------------------------------------------------------------------
# Public Subnets
# -----------------------------------------------------------------------------
resource "aws_subnet" "public" {
  count = local.is_aws ? length(var.public_subnets) : 0

  vpc_id                  = aws_vpc.this[0].id
  cidr_block              = var.public_subnets[count.index]
  map_public_ip_on_launch = true

  tags = {
    Name    = "${var.project_name}-public-${count.index}"
    Project = var.project_name
    Type    = "public"
  }
}

# -----------------------------------------------------------------------------
# Private Subnets
# -----------------------------------------------------------------------------
resource "aws_subnet" "private" {
  count = local.is_aws ? length(var.private_subnets) : 0

  vpc_id     = aws_vpc.this[0].id
  cidr_block = var.private_subnets[count.index]

  tags = {
    Name    = "${var.project_name}-private-${count.index}"
    Project = var.project_name
    Type    = "private"
  }
}

# -----------------------------------------------------------------------------
# Route Table for Public Subnets
# -----------------------------------------------------------------------------
resource "aws_route_table" "public" {
  count = local.is_aws ? 1 : 0

  vpc_id = aws_vpc.this[0].id

  route {
    cidr_block = "0.0.0.0/0"
    gateway_id = aws_internet_gateway.this[0].id
  }

  tags = {
    Name    = "${var.project_name}-public-rt"
    Project = var.project_name
  }
}

resource "aws_route_table_association" "public" {
  count = local.is_aws ? length(var.public_subnets) : 0

  subnet_id      = aws_subnet.public[count.index].id
  route_table_id = aws_route_table.public[0].id
}
