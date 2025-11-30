# 🐇 SD-RabbitMQ: Sistema de Mensageria Inteligente

![Status do Projeto](https://img.shields.io/badge/Status-Concluído-green)
![Java](https://img.shields.io/badge/Java-17%2B-orange)
![License](https://img.shields.io/badge/License-MIT-yellow)

Este repositório contém o sexto trabalho da disciplina de **Sistemas Distribuídos**, focado na implementação de um sistema de mensageria utilizando **RabbitMQ** para processamento assíncrono de imagens com classificação via Inteligência Artificial.

## 📄 Sobre o projeto

O objetivo deste projeto é demonstrar a arquitetura de produtores e consumidores em um ambiente distribuído. O sistema simula a geração de imagens que são processadas por diferentes filas de mensagens para classificação de expressões faciais e identificação de times.

O projeto utiliza a biblioteca **Smile** para as tarefas de Machine Learning integradas aos consumidores Java.

### 🎯 Objetivos específicos
- Implementar uma arquitetura baseada em eventos com **RabbitMQ**.
- Desenvolver consumidores independentes para tarefas distintas:
    - **Consumer Face:** Classificação de expressões (`happy` vs `sad`).
    - **Consumer Team:** Identificação de times (`RED`, `BLUE`, `GREEN`).
- Orquestrar múltiplos containers utilizando **Docker Compose**.

## 📊 Arquitetura e Dados

O sistema é composto por 4 containers principais:

- **RabbitMQ:** Broker de mensagens e painel de gerenciamento.
- **Generator:** Publica mensagens no exchange `images` com routing keys `face` ou `team`.
- **Consumer Face:** Processa mensagens da fila de faces.
- **Consumer Team:** Processa mensagens da fila de times.

## 🛠️ Tecnologias utilizadas

O projeto foi desenvolvido em **Java** e **Docker**. As principais tecnologias são:

- **Java 17+**: Linguagem principal dos serviços.
- **RabbitMQ**: Broker de mensageria.
- **Docker & Docker Compose**: Containerização e orquestração.
- **Maven**: Gerenciamento de dependências.
- **Smile**: Biblioteca de Machine Learning para Java.

## 🚀 Como executar

### Pré-requisitos
Certifique-se de ter o Docker e o Docker Compose instalados em sua máquina.

### Instalação

1. Clone o repositório:
   ```bash
   git clone https://github.com/seu-usuario/SD-RabbitMQ.git
   cd SD-RabbitMQ
   ```

2. Construa e inicie os containers:
   ```bash
   docker-compose up --build
   ```

3. Acompanhe os logs no terminal para verificar o processamento das mensagens.

4. (Opcional) Acesse o painel do RabbitMQ em `http://localhost:15672` (Usuário/Senha: `guest`).

## 📈 Resultados

O sistema processa imagens continuamente, exibindo nos logs a classificação realizada por cada consumidor.

> **Vídeo demonstrativo:** [Assista no YouTube](https://youtu.be/TTuTfr9IGSU)

## 📂 Estrutura do repositório

```
📂SD-RabbitMQ/
├── 📂 consumer-face/      # Consumidor para classificação facial
├── 📂 consumer-team/      # Consumidor para identificação de times
├── 📂 generator/          # Gerador de mensagens/imagens
├── 🐳 docker-compose.yml  # Orquestração dos containers
├── 📄 LICENSE             # Licença de uso
└── 📄 README.md           # Documentação do projeto
```

## 📝 Licença

Este projeto está sob a licença MIT. Veja o arquivo [LICENSE](LICENSE) para mais detalhes.
