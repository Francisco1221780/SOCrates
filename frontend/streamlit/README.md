# CyberRangers UI

UI simples em Streamlit, organizada com camadas de domínio, aplicação,
infraestrutura e apresentação.

## Executar

```powershell
cd frontend\streamlit
python -m venv .venv
.\.venv\Scripts\Activate.ps1
pip install -r requirements.txt
python -m streamlit run app.py
```

A interface ficará disponível em `http://localhost:8501`.

Para apontar para outro backend:

```powershell
$env:BACKEND_URL = "http://localhost:8080"
python -m streamlit run app.py
```
