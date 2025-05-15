# Tomcat Log File Handler

A Spring Boot application for analyzing and searching Tomcat log files. Supports file upload, log parsing, summary statistics, top messages, and advanced search with pagination, case sensitivity, and regular expressions.

---

## Features
- Upload and save log files
- Generate summary statistics
- Search logs with pagination
- Case-sensitive and regex-based search
- Automatically restores the last uploaded file on startup

---

## Getting Started

### Prerequisites
- Java 17
- Maven 3.8+

### Clone the repository
```bash
git clone https://github.com/kamilaabdilova/tomcat-log-file-handler.git
cd tomcat-log-file-handler
Собери проект с помощью Maven: mvn clean install
Создай файл конфигурации:
Создай файл src/main/resources/application.properties с таким содержимым:
server.port=8081
log.directory=./logs
spring.servlet.multipart.max-file-size=50MB
spring.servlet.multipart.enabled=true
Запусти приложение:mvn spring-boot:run
Файл логов:
	•	Загрузить файл логов: POST /api/logs/upload
	•	Получить сводку логов: GET /api/logs/analysis/summary
	•	Получить топ сообщений: GET /api/logs/analysis/top-messages?limit=10
	•	Поиск логов: GET /api/logs/search?query=error&regex=true&caseSensitive=true&page=1&size=10
Примеры использования API
Загрузка файла: curl -F "file=@/path/to/your/logfile.out" http://localhost:8081/api/logs/upload
Получить сводку логов: curl http://localhost:8081/api/logs/analysis/summary
Топ сообщений (limit 5): curl http://localhost:8081/api/logs/analysis/top-messages?limit=5
Поиск сообщений (регулярное выражение, без учета регистра): curl "http://localhost:8081/api/logs/search?query=error.*&regex=true&caseSensitive=false&page=1&size=10"
