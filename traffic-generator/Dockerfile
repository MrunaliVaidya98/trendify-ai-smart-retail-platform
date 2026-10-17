FROM python:3.12-alpine
WORKDIR /app
COPY traffic-generator/ .
RUN pip install --no-cache-dir -r requirements.txt
USER nobody
CMD ["python", "main.py"]
