# AIR TOUCH — Arquitetura e plano

## 1. O que preciso saber do seu tablet
- Marca e modelo (ex.: Samsung Galaxy Tab A9+).
- Versão do Android (Configurações > Sobre o tablet).
- Chip/RAM (define se dá para usar GPU e quantos quadros por segundo).
- Se tem câmera frontal (o projeto usa a frontal; cai para a traseira se não houver).
- Orientação de uso: horizontal (apoiado/em suporte) ou vertical.
- Se o Android Studio está instalado e se você consegue ligar a Depuração USB.

## 2. Limitações do Android (o que NÃO dá para presumir)
Estas são as restrições que moldam o projeto. Foram descritas a partir do que conheço da plataforma e **precisam ser confirmadas no seu aparelho/versão** nas Fases 2 e 3.

| Ação | Como seria feita | Limitação |
|---|---|---|
| Rolar, tocar, arrastar em OUTROS apps | `AccessibilityService.dispatchGesture` | Exige o usuário ligar o serviço manualmente; apps sensíveis (bancos) e janelas seguras bloqueiam; jogos podem ignorar ou tratar como bot. |
| Voltar, Início, Recentes, notificações | `performGlobalAction` do AccessibilityService | Só com o serviço ativo. |
| Cursor/ponteiro sobre outros apps | Janela de sobreposição (`SYSTEM_ALERT_WINDOW`) | Permissão especial concedida pelo usuário. |
| Câmera com outro app em primeiro plano | Foreground Service tipo câmera | Android restringe câmera em segundo plano; só um app usa a câmera por vez. Deve ser testado no seu aparelho. |
| Volume e mídia | `AudioManager` / eventos de mídia | Funciona; mídia depende de o app aceitar. |
| Abrir apps e documentos | `Intent` | Funciona. |
| Controlar PowerPoint/leitor de PDF de terceiros | Gestos de toque simulados (acessibilidade) | Frágil: depende do layout do app. |
| Digitar em outros apps | Teclado próprio (IME) ou `ACTION_SET_TEXT` | IME próprio é um projeto à parte; escrita no ar é experimental. |

**Alternativa tecnicamente mais sólida:** dentro do próprio AIR TOUCH, criar visualizador de PDF/slides (PdfRenderer) e área de jogos próprios, onde o controle é 100% confiável. Para outros apps, usar acessibilidade como "melhor esforço".

## 3. Arquitetura
Fluxo: **Câmera → Detecção → Reconhecimento → Despachante de ações → Controladores**.

Princípio de extensão: cada gesto é um valor de `Gesture`; cada ação será registrada no `GestureActionDispatcher` por perfil (Fase 2). Adicionar gesto/ação não exige mexer no resto.

Módulos (✅ = entregue na Fase 1):
- ✅ `CameraManager` — CameraX, preview, quadros rotacionados/espelhados.
- ✅ `HandDetectionEngine` — MediaPipe Hand Landmarker (LIVE_STREAM, 21 pontos/mão, 1–2 mãos).
- ✅ `GestureClassifier` + `GestureRecognitionEngine` — regras geométricas + debounce.
- ⏳ `GestureCalibration`, `GestureActionDispatcher`, `VirtualMouse`, `AirWriting`, `PresentationController`, `DocumentController`, `GameController`, `AccessibilityController`, `SettingsManager` — fases seguintes.

## 4. Estrutura de pastas
```
airtouch/
├── settings.gradle.kts / build.gradle.kts / gradle.properties
├── ARQUITETURA.md
└── app/
    ├── build.gradle.kts
    └── src/
        ├── main/
        │   ├── AndroidManifest.xml
        │   ├── assets/hand_landmarker.task      (baixado pelo Gradle)
        │   ├── res/values/{strings,themes}.xml
        │   └── java/com/airtouch/
        │       ├── MainActivity.kt
        │       ├── AirTouchViewModel.kt
        │       ├── model/HandModels.kt
        │       ├── camera/CameraManager.kt
        │       ├── detection/HandDetectionEngine.kt
        │       ├── gesture/{Gesture,GestureClassifier,GestureRecognitionEngine}.kt
        │       └── ui/{AirTouchScreen,CameraPreview,HandOverlay}.kt + theme/Theme.kt
        └── test/java/com/airtouch/gesture/GestureClassifierTest.kt
```
Planejado (ainda não criado): `control/`, `mouse/`, `writing/`, `presentation/`, `documents/`, `games/`, `accessibility/`, `settings/`.

## 5. Tecnologias e por quê
- **MediaPipe Hand Landmarker**: modelo pronto, roda offline, 21 pontos por mão, 2 mãos, otimizado para mobile.
- **CameraX**: API simples e compatível entre aparelhos; `ImageAnalysis` com descarte de quadros atrasados (baixa latência).
- **Kotlin + Compose**: interface moderna e reativa (StateFlow).
- **Regras geométricas agora, TensorFlow Lite depois**: os 5 gestos básicos não precisam de treino; TFLite entra na Fase 4 para gestos personalizados.
- **OpenCV**: não incluído — o MediaPipe já cobre o necessário e OpenCV aumentaria o APK sem benefício na Fase 1.
- **AccessibilityService**: Fase 3.

## 6. Global x dentro do app
- **Dentro do app (confiável):** painel, mouse virtual no próprio app, apresentações/PDF próprios, escrita no ar, jogos próprios, personalização de gestos.
- **Global (melhor esforço, depende de permissões):** toques/rolagem em outros apps, voltar/início/recentes, ponteiro sobreposto, câmera com outro app aberto.

## 7. Fase 1 — o que faz
Abre a câmera, detecta 1 mão, desenha os 21 pontos, reconhece Mão aberta, Punho, Polegar ↑, Polegar ↓ e Indicador apontado (com debounce de 4 quadros), mostra gesto, confiança, FPS, latência e histórico. Indicador "CÂMERA ATIVA" e botão para desligar a câmera na hora.

Premissas do reconhecimento: mão em pé na imagem; polegar ↑/↓ só é reconhecido com os outros 4 dedos fechados.
