# Instrucciones para Personalizar el Correo de Verificación

## 1. Configuración del Código (✅ YA COMPLETADO)

Ya actualicé el código en `SignupFragment.java` para usar `ActionCodeSettings` que apunta a tu página personalizada:
- URL: `https://centrointegralalerce.web.app/password-reset.html`

## 2. Personalizar la Plantilla del Correo en Firebase Console

Para cambiar el diseño del correo electrónico que Firebase envía, sigue estos pasos:

### Paso 1: Acceder a Firebase Console
1. Ve a https://console.firebase.google.com
2. Selecciona tu proyecto: **centrointegralalerce**
3. En el menú lateral, ve a **Authentication** (Autenticación)
4. Haz clic en la pestaña **Templates** (Plantillas)

### Paso 2: Editar la Plantilla de Verificación de Email
1. Busca la plantilla **Email address verification** (Verificación de dirección de correo electrónico)
2. Haz clic en el icono de lápiz para editar
3. Personaliza el contenido:

#### Opciones de Personalización:

**Asunto del correo:**
```
Verifica tu correo electrónico - Centro Integral Alerce
```

**Remitente:**
- Nombre: `Centro Integral Alerce`
- Email: `noreply@centrointegralalerce.firebaseapp.com`

**Cuerpo del mensaje (Ejemplo en español):**
```
Hola %DISPLAY_NAME%,

Gracias por registrarte en Centro Integral Alerce.

Para completar tu registro, por favor verifica tu dirección de correo electrónico haciendo clic en el siguiente enlace:

%LINK%

Si no creaste una cuenta con nosotros, puedes ignorar este mensaje.

Saludos,
El equipo de Centro Integral Alerce

---
Este es un correo automático, por favor no respondas a este mensaje.
```

### Paso 3: Personalizar el Idioma
1. En la sección de idioma, selecciona **Español (es)**
2. Guarda los cambios

## 3. Personalizar la Página de Destino (password-reset.html)

Ya tienes el archivo `password-reset.html` que maneja la verificación. Asegúrate de que:

- ✅ El archivo está en la raíz de tu proyecto
- ✅ Está desplegado en Firebase Hosting
- ✅ Maneja el modo `verifyEmail` correctamente (ya lo hace)

## 4. Desplegar los Cambios en Firebase Hosting

Para que los cambios surtan efecto, despliega tu sitio:

```bash
cd "C:\Users\diego\OneDrive\Documentos\Android otravez"
firebase deploy --only hosting
```

## 5. Crear un Template HTML Personalizado para Email

Nota: Firebase no permite usar HTML completamente personalizado en los correos de verificación de forma nativa. Solo puedes personalizar el texto.

**Sin embargo**, hay alternativas:

### Opción A: Usar Firebase Cloud Functions (Recomendado)
Crear una Cloud Function que envíe correos personalizados usando un servicio como SendGrid, Mailgun, o Nodemailer.

### Opción B: Usar la Personalización Básica de Firebase
Usar las plantillas de Firebase (texto plano con variables) que ya están disponibles en la consola.

## 6. Verificar que Funciona

1. Registra un usuario nuevo en la app
2. Revisa el correo que llega
3. Verifica que:
   - El asunto sea el correcto
   - El remitente sea "Centro Integral Alerce"
   - El enlace te lleve a tu página personalizada
   - Al hacer clic, te muestre tu diseño personalizado

## Notas Importantes

- **El diseño del correo electrónico** lo controla Firebase, solo puedes personalizar el texto
- **El diseño de la página de destino** (password-reset.html) ya está personalizado con tu branding
- Para correos completamente personalizados, necesitas usar Firebase Cloud Functions

## URLs Importantes

- Tu página personalizada: https://centrointegralalerce.web.app/password-reset.html
- Firebase Console: https://console.firebase.google.com
- Proyecto: centrointegralalerce
