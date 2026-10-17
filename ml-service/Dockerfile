FROM python:3.12-slim
WORKDIR /app
ENV PYTHONPATH=/app
COPY ml-service/requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY ml-service/app app
COPY ml-service/tests tests
COPY ml-service/pytest.ini .
COPY lambda /lambda
RUN mkdir -p artifacts data && pytest -q
EXPOSE 8001
CMD ["uvicorn","app.main:app","--host","0.0.0.0","--port","8001"]
