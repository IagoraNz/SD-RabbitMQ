# RabbitMQ IA System (TRABALHO 06)

4 containers:
- rabbitmq (management)
- generator (Java) -> publica mensagens no exchange `images` com routing keys `face` ou `team`
- consumer-face (Java + Smile) -> classifica expressão (happy/sad)
- consumer-team (Java + Smile) -> identifica time (RED/BLUE/GREEN)

### Requisitos
- Docker & Docker Compose
- Internet para baixar imagens base (Maven dependencies) na primeira build

### Como rodar (passos):
1. Abra o WSL (pasta do projeto `rabbitmq-ia-system`)
2. `docker-compose up --build`  (ou `docker compose up --build`)
3. Aguarde os containers entrarem em healthy/UP

Painel RabbitMQ: http://localhost:15672  
Usuário: guest / Senha: guest

Ver logs:
- `docker-compose logs -f generator`
- `docker-compose logs -f consumer-face`
- `docker-compose logs -f consumer-team`

Para parar:
- `docker-compose down -v`

### Testes
- Observe filas e tamanho (Management UI).
- O gerador envia ~5 msg/s; consumidores processam ~1 msg/s para enfileirar mensagens visivelmente.

(Projeto implementado conforme TRABALHO 06). Referência do enunciado: ver TRABALHO 06.docx.pdf.