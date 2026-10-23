# 📺📱 Verifique os Fatos • Smart TV & Mobile (Android APK)

Aplicativo Android nativo ultra-leve (**~58 KB**) desenvolvido para acompanhar os debates eleitorais ao vivo em **Smart TVs (Android TV, Google TV, Fire TV, TV Box)** e **Smartphones Android**, exibindo um **Popup Flutuante (Overlay)** sobre qualquer vídeo ou transmissão toda vez que uma nova checagem de fatos entra no ar em [`verifiqueosfatos.com/posts.json`](https://verifiqueosfatos.com/posts.json).

---

## ✨ Funcionalidades

- **📺 Feito para Smart TVs e Celulares:**
  - Suporte nativo ao **Android TV / Google TV Launcher (Leanback)** com banner dedicado.
  - Navegação 100% compatível com **Controle Remoto (D-Pad)** com destaque visual de foco e também com **Touchscreen**.
- **💬 Popup Flutuante Ao Vivo (Overlay sem pausar seu vídeo):**
  - Aparece suavemente por cima do app que você estiver assistindo (Globoplay, YouTube, TV ao vivo, navegador) usando `FLAG_NOT_FOCUSABLE` — ou seja, **não pausa o vídeo nem rouba o foco do controle remoto da TV**.
  - Exibe a foto circular do candidato (`Lula` ou `Flávio`), o selo colorido (**Verdadeiro**, **Falso** ou **Parcial**), a citação (*"Disse que..."*), a explicação detalhada (*"Verificação"*) e uma barra de contagem regressiva antes de fechar sozinho.
  - **Auto-scroll inteligente:** Se o texto da verificação for longo, o card rola o texto suavemente sozinho após alguns segundos para você ler do sofá sem precisar tocar no controle remoto.
- **⚙️ Personalização Rápida na Tela Inicial:**
  - **Posição na Tela:** `Canto Inferior Direito (TV)`, `Canto Superior Direito`, `Rodapé (Estilo GC de Telejornal)` ou `Topo Central`.
  - **Tempo de Exibição:** `15s`, `20s`, `30s` ou `45s`.
  - **Alerta Sonoro:** Bip sutil opcional ao entrar nova checagem.
  - **Botão "Testar em 5s":** Permite clicar, voltar para o seu app de vídeo/TV e ver o popup aparecendo ao vivo sobre o vídeo 5 segundos depois.
- **🪶 Filosofia Mínima (Igual ao repo original):**
  - Zero bibliotecas de terceiros, zero rastreadores, ~58 KB de tamanho final.

---

## 📥 Download Direto do APK

Baixe a versão mais recente diretamente na aba **[Releases](https://github.com/jonnysundae/verifiqueosfatos-android/releases/latest)**:

👉 **`https://github.com/jonnysundae/verifiqueosfatos-android/releases/latest/download/verifiqueosfatos.apk`**

---

## 📺 Como instalar e configurar na Smart TV

1. Instale o arquivo `verifiqueosfatos.apk` na sua TV (usando o app **Downloader**, **Send Files to TV** ou pendrive).
2. Abra o app **Verifique os Fatos**.
3. Clique no botão amarelo **"PERMITIR SOBREPOR A OUTROS APPS"** e ative a permissão para o aplicativo.
   - *Dica para TVs/TV Boxes que ocultam o menu de sobreposição nas configurações:* Se a sua TV não abrir a tela de permissão, você pode ativar via ADB com o comando:
     ```bash
     adb shell appops set com.jonnysundae.verifiqueosfatos SYSTEM_ALERT_WINDOW allow
     ```
4. Confirme que o status no topo está **`● MONITORANDO AO VIVO`** (ou clique em **Testar em 5s**), abra a transmissão do debate e acompanhe!

---

## 🛠️ Compilando localmente (Windows PowerShell)

Para recompilar e assinar o `.apk` instantaneamente:

```powershell
powershell -ExecutionPolicy Bypass -File .\build-apk.ps1
```
