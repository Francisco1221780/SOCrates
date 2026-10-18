# Regras de Protecao de Branches

## Configuracao manual obrigatoria (Settings do GitHub)

Estas regras **nao podem ser criadas por workflow**: tem de ser feitas nas Settings do repositorio no GitHub.

### Como configurar (Settings > Branches > Add branch ruleset)

#### Proteger a `main`

1. Ir a **Settings > Branches > Add rule** (ou **Rulesets**)
2. Branch name pattern: `main`
3. Ativar:
   - [x] **Require a pull request before merging**
     - Required approvals: `1`
     - [x] Dismiss stale pull request approvals when new commits are pushed
   - [x] **Require status checks to pass before merging**
     - Adicionar: `Drools` (o job do workflow `drools.yml`)
     - Adicionar: `Prolog` (o job do workflow `prolog.yml`, quando existir)
     - [x] Require branches to be up to date before merging
   - [x] **Do not allow bypassing the above settings** (IMPORTANTE: aplica-se a todos, incluindo admins)
   - [x] **Restrict who can push to matching branches** → so `develop`
   - [x] **Block force pushes**
   - [x] **Require linear history** (opcional, mantem o historico limpo)

#### Proteger a `develop`

1. Branch name pattern: `develop`
2. Ativar:
   - [x] **Require a pull request before merging**
     - Required approvals: `1`
   - [x] **Require status checks to pass before merging**
     - Adicionar: `Drools`
   - [x] **Block force pushes**

### Fluxo de trabalho correto

```
feature/minha-funcionalidade
        |
        v  (PR para develop)
      develop   <-- unico caminho para chegar a main
        |
        v  (PR para main, so quando uma versao esta pronta)
       main
```

### Apagar branches automaticamente

O workflow `.github/workflows/apagar_branch_apos_merge.yml` apaga a branch feature
automaticamente quando um PR e aceite. Nao afeta `develop` nem `main`.


- **Settings > General > Pull Requests > Automatically delete head branches** (checkbox)

