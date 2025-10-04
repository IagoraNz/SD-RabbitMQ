# 🐇 SD-RabbitMQ: Sexto Trabalho de SD com IA System

Sistema de mensageria com **RabbitMQ** e consumidores em **Java + Smile**, para classificação de imagens e identificação de times.

**Vídeo apresentativo:** https://youtu.be/TTuTfr9IGSU

## 🐳 Containers do Projeto

O projeto possui 4 containers:

| Container | Descrição |
|-----------|-----------|
| `rabbitmq` | Painel de gerenciamento (Management UI) |
| `generator` | Publica mensagens no exchange `images` <br> Routing keys: `face` ou `team` |
| `consumer-face` | Classifica expressões faciais (`happy` / `sad`) |
| `consumer-team` | Identifica time (`RED` / `BLUE` / `GREEN`) |

## 💻 Estrutura do Projeto

```
SD-RabbitMQ
├── 🐰 consumer-face
│    ├── 📂 src/main/java/com/example/consumerface
│    │    └── 📝 ConsumerFace.java
│    ├── 📂 target
│    ├── 🐳 Dockerfile
│    └── 📄 pom.xml
├── 🏆 consumer-team
│    ├── 📂 src/main/java/com/example/consumerteam
│    │    └── 📝 ConsumerTeam.java
│    ├── 📂 target
│    ├── 🐳 Dockerfile
│    └── 📄 pom.xml
├── ⚡ generator
│    ├── 📂 src
│    ├── 📂 target
│    ├── 🐳 Dockerfile
│    ├── 📄 pom.xml
│    ├── 📄 .gitignore
│    └── 📄 README.md
├── 🐳 docker-compose.yml
└── 📄 LICENSE
```

## ⚙️ Requisitos

- Docker & Docker Compose  
- Internet para baixar imagens base e dependências Maven  
- Java 17+  
- Maven

## 🖥️ Instalação do RabbitMQ no Ubuntu

1. Atualizar as dependências
```bash
sudo apt update && sudo apt upgrade -y
```

2. Instalar dependências
```bash
sudo apt install curl gnupg apt-transport-https -y
```

3. Adicionar chave GPG do RabbitMQ
```bash
curl -1sLf 'https://keys.openpgp.org/vks/v1/by-fingerprint/0A9AF2115F4687BD29803A206B73A36E6026DFCA' | \
  gpg --dearmor | sudo tee /usr/share/keyrings/com.rabbitmq.team.gpg > /dev/null
```

4. Adicionar repositórios (Erlang + RabbitMQ)
```bash
echo "deb [signed-by=/usr/share/keyrings/com.rabbitmq.team.gpg] https://dl.cloudsmith.io/public/rabbitmq/rabbitmq-erlang/deb/ubuntu $(lsb_release -sc) main" | \
  sudo tee /etc/apt/sources.list.d/rabbitmq-erlang.list

echo "deb [signed-by=/usr/share/keyrings/com.rabbitmq.team.gpg] https://dl.cloudsmith.io/public/rabbitmq/rabbitmq-server/deb/ubuntu $(lsb_release -sc) main" | \
  sudo tee /etc/apt/sources.list.d/rabbitmq-server.list
```

5. Atualizar lista de pacotes
```
sudo apt update
```
6. Instalar Erlang e RabbitMQ
```
sudo apt install rabbitmq-server -y
```
7. Iniciar RabbitMQ no WSL
```
sudo service rabbitmq-server start
```
8. (Opcional) Habilitar painel web
```
sudo rabbitmq-plugins enable rabbitmq_management
```

9. Buildar e subir a aplicação
```
docker-compose up --build
```

10. Visualize e confira o funcionamento adequado da aplicação
