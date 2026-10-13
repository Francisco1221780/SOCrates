# CyberRangers

Motor de inferência CyberRangers em Java, com Spring Boot e integração prevista
com Drools e Prolog. O backend é gerido com Maven.

## Requisitos

- JDK 25
- Ligação à internet na primeira execução do Maven Wrapper

## Executar

Na raiz do repositório, execute no PowerShell:

```powershell
Set-Location backend\cyber
.\mvnw.cmd spring-boot:run
```

O Maven Wrapper descarrega e utiliza Maven 3.10.0, sem depender de uma
instalação global.

Para executar os testes:

```powershell
Set-Location backend\cyber
.\mvnw.cmd test
```

Em alternativa, dentro de `backend\cyber`:

```powershell
.\mvnw.cmd spring-boot:run
.\mvnw.cmd test
```