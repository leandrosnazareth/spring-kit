# Layout Test

O layout deveria estar assim:

```
+------------------------+------------------------+
|                        |                        |
|    LEFT PANEL         |    RIGHT PANEL        |
|    (Config + Meta)    |    (Dependencies)     |
|                        |                        |
|    [Flex: 1]          |    [Width: 450px]     |
|                        |                        |
+------------------------+------------------------+
```

## CSS Aplicado:

- `.content { display: flex; flex-direction: row; }`
- `form { display: contents; }` <- Isso faz o form não criar container
- `.left-panel { flex: 1; }` <- Ocupa espaço restante
- `.right-panel { width: 450px; flex-shrink: 0; }` <- Largura fixa

## Verificação:

1. Abra o DevTools (F12)
2. Inspecione o elemento `.content`
3. Verifique se tem `display: flex` e `flex-direction: row`
4. Verifique se `.left-panel` e `.right-panel` são filhos diretos de `.content`

Se as dependências estão embaixo:
- O navegador pode não suportar `display: contents`
- Ou precisa dar Ctrl+F5 para limpar cache

## Solução Alternativa:

Se não funcionar, remova o form wrapper e use JavaScript para submit.
