variable "aws_region" {
  type    = string
  default = "us-west-2"
}
variable "environment" {
  type    = string
  default = "dev"
  validation {
    condition     = contains(["dev", "staging", "prod"], var.environment)
    error_message = "environment must be dev, staging, or prod"
  }
}
variable "ml_batch_url" {
  type        = string
  description = "HTTPS endpoint for the batch ML service"
}
