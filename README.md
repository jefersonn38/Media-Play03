# Media Play (Android)

Player de música em Kotlin + Jetpack Compose + Media3 (ExoPlayer), com visual liquid glass.
Lê as músicas do aparelho, mostra capas e toca em segundo plano.

## Jeito 1: Android Studio
1. Abra a pasta no Android Studio e aguarde a sincronização do Gradle.
2. Conecte o celular (depuração USB) e toque em Run.

## Jeito 2: APK pelo GitHub (sem instalar nada)
1. Crie um repositório no GitHub e envie todo o conteúdo desta pasta (inclusive .github).
2. Aba Actions, "Build APK", Run workflow.
3. Quando terminar, baixe o artefato "MediaPlay-apk", extraia e instale o app-debug.apk no celular
   (permita instalar de fontes desconhecidas).

Requer Android 10 ou superior.

## Ícone
O ícone do app já está incluído (ícone adaptativo em app/src/main/res). Para a Play Store, use store/icon-512.png no campo de ícone do app.

## Modo conectado
A aba "Conectado" mostra e controla o que toca no YouTube Music, Deezer, Amazon Music, Spotify e outros.
Funciona pelas sessões de mídia do Android e exige que o usuário ative o acesso às notificações do Media Play
(a aba abre a tela certa do sistema). O app só lê título, artista e capa. Cite isso na política de privacidade
e na declaração de permissões da Play Store.

## Dica para APK instalado fora da Play Store
No Android 13 ou superior, o acesso às notificações (Modo conectado) pode vir bloqueado. Abra
Configurações > Apps > Media Play > menu ⋮ > "Permitir configurações restritas" e ative o acesso de novo.

## Telas
Início (Mais tocadas 3x3 + álbuns sanfonados), Música (pesquisa, faixas, álbuns em disco, artistas),
Conectado e Ajustes. A tela de reprodução tem a capa com o disco girando e o botão play/pause em esfera colorida.

## Versão 2
- Capas: usa a imagem do arquivo; se não houver, gera uma capa colorida (a mesma para o mesmo álbum).
- Biblioteca: lista as pastas do aparelho (as que o Android já indexou como música) e as músicas de cada uma.
- Modo conectado: cartão na tela inicial.
- Ajustes: qualidade, equalizador real, saída de áudio, formatos, codecs do telefone, codecs Bluetooth e permissões.

## Versão 3
- Tela de reprodução: título e artista no topo, capa com meio disco saindo, capa da próxima música atrás, animação ao pular
  (a capa entra pelo lado e o disco "sai" do álbum), barra de progresso em LEDs.
- Música: aba Favoritos (estrela na tela de reprodução). Artistas: cada artista abre uma única lista com todas as músicas.
- Biblioteca: "Pastas do aparelho" (como estão no telefone) e "Pastas de artistas".
- Correção: a capa da música anterior não ficava mais presa ao pular de faixa.
- Modo conectado: botão para abrir as informações do app e liberar as configurações restritas.
