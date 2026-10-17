locals {
  name = "trendify-${var.environment}"
  tags = {
    Project     = "Trendify"
    Environment = var.environment
    ManagedBy   = "Terraform"
  }
}
data "aws_caller_identity" "current" {}
resource "aws_dynamodb_table" "main" {
  name         = local.name
  billing_mode = "PAY_PER_REQUEST"
  hash_key     = "PK"
  range_key    = "SK"
  attribute {
    name = "PK"
    type = "S"
  }
  attribute {
    name = "SK"
    type = "S"
  }
  point_in_time_recovery { enabled = true }
  server_side_encryption { enabled = true }
  tags = local.tags
}
resource "aws_s3_bucket" "models" {
  bucket = "${local.name}-models-${data.aws_caller_identity.current.account_id}"
  tags   = local.tags
}
resource "aws_s3_bucket_versioning" "models" {
  bucket = aws_s3_bucket.models.id
  versioning_configuration { status = "Enabled" }
}
resource "aws_s3_bucket_server_side_encryption_configuration" "models" {
  bucket = aws_s3_bucket.models.id
  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}
data "archive_file" "lambda" {
  type        = "zip"
  source_file = "${path.module}/../../lambda/handler.py"
  output_path = "${path.module}/forecast.zip"
}
resource "aws_iam_role" "lambda" {
  name               = "${local.name}-forecast"
  assume_role_policy = jsonencode({ Version = "2012-10-17", Statement = [{ Action = "sts:AssumeRole", Effect = "Allow", Principal = { Service = "lambda.amazonaws.com" } }] })
  tags               = local.tags
}
resource "aws_iam_role_policy" "lambda" {
  role = aws_iam_role.lambda.id
  policy = jsonencode({ Version = "2012-10-17", Statement = [
    { Effect = "Allow", Action = ["logs:CreateLogGroup", "logs:CreateLogStream", "logs:PutLogEvents"], Resource = "arn:aws:logs:${var.aws_region}:${data.aws_caller_identity.current.account_id}:*" },
    { Effect = "Allow", Action = ["dynamodb:GetItem", "dynamodb:PutItem", "dynamodb:UpdateItem", "dynamodb:Query"], Resource = aws_dynamodb_table.main.arn },
    { Effect = "Allow", Action = ["s3:PutObject", "s3:GetObject"], Resource = "${aws_s3_bucket.models.arn}/*" }
  ] })
}
resource "aws_lambda_function" "forecast" {
  function_name    = "${local.name}-forecast-refresh"
  role             = aws_iam_role.lambda.arn
  handler          = "handler.handler"
  runtime          = "python3.12"
  filename         = data.archive_file.lambda.output_path
  source_code_hash = data.archive_file.lambda.output_base64sha256
  timeout          = 30
  memory_size      = 256
  environment {
    variables = { ML_BATCH_URL = var.ml_batch_url, TABLE_NAME = aws_dynamodb_table.main.name, MODEL_BUCKET = aws_s3_bucket.models.id }
  }
  tags = local.tags
}
resource "aws_cloudwatch_event_rule" "daily" {
  name                = "${local.name}-daily-forecast"
  schedule_expression = "cron(15 2 * * ? *)"
  tags                = local.tags
}
resource "aws_cloudwatch_event_target" "lambda" {
  rule = aws_cloudwatch_event_rule.daily.name
  arn  = aws_lambda_function.forecast.arn
  retry_policy {
    maximum_event_age_in_seconds = 3600
    maximum_retry_attempts       = 2
  }
}
resource "aws_lambda_permission" "events" {
  statement_id  = "AllowEventBridge"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.forecast.function_name
  principal     = "events.amazonaws.com"
  source_arn    = aws_cloudwatch_event_rule.daily.arn
}
resource "aws_cloudwatch_log_group" "lambda" {
  name              = "/aws/lambda/${aws_lambda_function.forecast.function_name}"
  retention_in_days = 30
  tags              = local.tags
}
resource "aws_cloudwatch_metric_alarm" "errors" {
  alarm_name          = "${local.name}-forecast-errors"
  namespace           = "AWS/Lambda"
  metric_name         = "Errors"
  statistic           = "Sum"
  period              = 300
  evaluation_periods  = 1
  threshold           = 0
  comparison_operator = "GreaterThanThreshold"
  dimensions          = { FunctionName = aws_lambda_function.forecast.function_name }
  treat_missing_data  = "notBreaching"
  tags                = local.tags
}
output "table_name" { value = aws_dynamodb_table.main.name }
output "model_bucket" { value = aws_s3_bucket.models.id }
