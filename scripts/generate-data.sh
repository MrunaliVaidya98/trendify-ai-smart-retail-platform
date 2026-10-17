#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")/../ml-service"
python -c "from pathlib import Path; from app.pipeline import generate_sales; generate_sales(Path('../datasets/generated/sales.csv'))"
