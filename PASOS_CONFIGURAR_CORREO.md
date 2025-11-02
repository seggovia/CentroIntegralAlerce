# Pasos para Configurar el Correo de Verificación Personalizado

## ✅ Lo que ya hiciste:
1. Actualizaste el código en `SignupFragment.java` con `ActionCodeSettings`
2. Ejecutaste `firebase deploy --only hosting`
3. Tu página `password-reset.html` ya está desplegada

## 🎯 Problema actual:
El correo que llega sigue usando la plantilla básica de Firebase (texto plano). Necesitas personalizarlo.

## 📋 Solución: 3 Opciones

### **OPCIÓN 1: Personalización Básica en Firebase Console (MÁS FÁCIL)** ⭐ RECOMENDADO

Esta es la forma más rápida y no requiere código adicional.

#### Pasos:

1. **Ir a Firebase Console**
   - Abre: https://console.firebase.google.com
   - Selecciona tu proyecto: `centrointegralalerce`

2. **Acceder a Templates de Authentication**
   - En el menú lateral: **Authentication** → **Templates**
   - Encuentra: **Email address verification** (Verificación de dirección de correo)

3. **Editar la plantilla**
   - Haz clic en el ícono de **lápiz** ✏️
   - Cambia el **idioma** a: `Spanish (es)`

4. **Personalizar el contenido**

   **Nombre del remitente:**
   ```
   Centro Integral Alerce
   ```

   **Asunto:**
   ```
   Verifica tu correo electrónico - Centro Integral Alerce
   ```

   **Cuerpo del mensaje:**
   ```
   Hola,

   Gracias por registrarte en Centro Integral Alerce.

   Para completar tu registro y activar tu cuenta, verifica tu dirección de correo haciendo clic en el siguiente enlace:

   %LINK%

   Si no creaste una cuenta con nosotros, puedes ignorar este mensaje de forma segura.

   Gracias,
   El equipo de Centro Integral Alerce

   ---
   Este es un correo automático, por favor no respondas a este mensaje.
   ```

5. **Guardar cambios**
   - Haz clic en **Guardar**

6. **Probar**
   - Crea una cuenta nueva en tu app
   - Revisa el correo que llega

**✅ VENTAJAS:**
- Fácil y rápido (5 minutos)
- No requiere código adicional
- Funciona de inmediato

**❌ DESVENTAJAS:**
- Diseño básico (solo texto, sin HTML personalizado)
- No puedes agregar el gradiente verde-azul del branding

---

### **OPCIÓN 2: Usar Firebase Extensions con SendGrid (INTERMEDIO)**

Esta opción te permite usar plantillas HTML completamente personalizadas.

#### Requisitos:
- Cuenta de SendGrid (tiene plan gratuito)
- Configurar Firebase Extensions

#### Pasos:

1. **Crear cuenta en SendGrid**
   - Ve a: https://sendgrid.com/
   - Regístrate gratis (incluye 100 emails/día gratis)

2. **Obtener API Key de SendGrid**
   - En SendGrid: Settings → API Keys → Create API Key
   - Guarda la clave generada

3. **Instalar Firebase Extension**
   ```bash
   firebase ext:install sendgrid/sendgrid-email
   ```

4. **Configurar la extensión**
   - Te pedirá el API Key de SendGrid
   - Configura el remitente: `noreply@centrointegralalerce.firebaseapp.com`

5. **Subir tu plantilla HTML**
   - Usa la plantilla que creé: `email-templates/verification-email.html`
   - Súbela a SendGrid Dynamic Templates

6. **Modificar el código de la app**
   - En lugar de `sendEmailVerification()`, usarás Firestore + Cloud Function
   - La Cloud Function enviará el email usando SendGrid

**✅ VENTAJAS:**
- HTML completamente personalizado
- Tu branding (colores, gradiente, etc.)
- Profesional

**❌ DESVENTAJAS:**
- Más complejo de configurar
- Requiere cuenta externa (SendGrid)
- Necesitas Cloud Functions

---

### **OPCIÓN 3: Personalización con Cloud Functions (AVANZADO)**

Crear una Cloud Function que envíe correos personalizados usando Nodemailer.

#### Pasos resumidos:

1. **Inicializar Cloud Functions**
   ```bash
   firebase init functions
   ```

2. **Instalar dependencias**
   ```bash
   cd functions
   npm install nodemailer
   ```

3. **Crear función que envíe correos HTML**
4. **Modificar la app para usar la Cloud Function**

**✅ VENTAJAS:**
- Control total del diseño
- Sin servicios externos
- Todo en Firebase

**❌ DESVENTAJAS:**
- Más código
- Requiere conocimientos de Node.js
- Configuración compleja

---

## 🎯 Mi Recomendación

Para tu caso, te recomiendo **empezar con la OPCIÓN 1** porque:

1. ✅ Es rápida (menos de 5 minutos)
2. ✅ No requiere código adicional
3. ✅ Ya funciona con tu `password-reset.html` personalizado
4. ✅ El usuario ve tu branding cuando hace clic en el enlace

El correo será texto plano, pero la **experiencia del usuario será buena** porque:
- El correo llega rápido
- El texto está personalizado con tu marca
- **Al hacer clic**, ve tu página hermosa con el gradiente verde-azul

Más adelante, si necesitas HTML en el correo, podemos implementar la OPCIÓN 2 (SendGrid).

---

## 📝 Próximos Pasos RECOMENDADOS

1. **Ahora mismo:**
   - Ve a Firebase Console
   - Personaliza el texto del correo (OPCIÓN 1)
   - Prueba creando una cuenta

2. **Después (opcional):**
   - Si quieres HTML personalizado, avísame
   - Te ayudo a configurar SendGrid (OPCIÓN 2)

---

## 🔗 Enlaces Útiles

- Firebase Console: https://console.firebase.google.com/project/centrointegralalerce/authentication/emails
- Tu página personalizada: https://centrointegralalerce.web.app/password-reset.html
- Plantilla HTML creada: `email-templates/verification-email.html`

---

## ❓ ¿Necesitas Ayuda?

Si quieres implementar la OPCIÓN 2 (SendGrid) para tener el HTML personalizado completo, avísame y te guío paso a paso.
