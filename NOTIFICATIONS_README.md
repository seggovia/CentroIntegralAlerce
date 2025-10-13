# Sistema de Notificaciones - Centro Integral Alerce

## 📋 Descripción General

El sistema de notificaciones automáticas permite avisar con anticipación sobre la ejecución de actividades. Cada actividad puede definir sus días de aviso previo, y al crear una actividad, el sistema configura automáticamente las notificaciones correspondientes.

## 🚀 Características Principales

### ✅ Funcionalidades Implementadas

- **Notificaciones Automáticas**: Se programan automáticamente al crear actividades
- **Configuración Flexible**: Cada actividad puede definir sus días de aviso previo
- **Notificaciones Push Locales**: Alertas inmediatas en el dispositivo
- **Gestión de Estados**: Control de notificaciones leídas/no leídas
- **Servicio en Segundo Plano**: Procesamiento automático de notificaciones pendientes
- **Persistencia**: Las notificaciones se almacenan en Firestore

### 🎯 Tipos de Notificaciones

1. **Recordatorios de Actividad**: Avisos sobre actividades programadas
2. **Cancelaciones**: Notificaciones cuando se cancela una actividad
3. **Reagendamientos**: Alertas sobre cambios de fecha/hora

## 🏗️ Arquitectura del Sistema

### Componentes Principales

```
📁 services/
├── NotificationService.java          # Servicio principal de notificaciones
├── NotificationBackgroundService.java # Servicio en segundo plano
└── NotificationBootReceiver.java     # Receptor para iniciar al arrancar

📁 repositories/
└── NotificacionRepository.java       # Gestión de datos en Firestore

📁 models/
└── Notificacion.java                # Modelo de datos

📁 ui/
├── NotificacionesFragment.java       # Fragmento para mostrar notificaciones
└── NotificacionesAdapter.java        # Adaptador para RecyclerView
```

### Flujo de Trabajo

1. **Creación de Actividad** → Se configura `diasAvisoPrevio`
2. **Programación Automática** → Se calculan fechas de notificación
3. **Almacenamiento** → Se guardan en Firestore
4. **Procesamiento** → El servicio en segundo plano las procesa
5. **Envío** → Se muestran como notificaciones push locales

## 🔧 Configuración y Uso

### 1. Configuración de Permisos

En `AndroidManifest.xml`:
```xml
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.WAKE_LOCK" />
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
```

### 2. Creación de Actividades con Notificaciones

```java
// En ActivityFormFragment.java
int diasAvisoPrevio = Integer.parseInt(getText(etDiasAvisoPrevio));

// Al crear la actividad, se programan automáticamente las notificaciones
programarNotificacionesActividad(activityId, nombreActividad, modoPeriodica, timestamps, diasAvisoPrevio);
```

### 3. Uso del Servicio de Notificaciones

```java
NotificationService notificationService = new NotificationService(context);

// Programar notificaciones para una actividad
notificationService.programarNotificacionesActividad(actividad, usuariosNotificar);

// Programar notificaciones para una cita
notificationService.programarNotificacionesCita(cita, actividad, usuariosNotificar);

// Enviar notificación inmediata
notificationService.enviarNotificacionInmediata("Título", "Mensaje", notificationId);
```

### 4. Gestión de Notificaciones

```java
NotificacionRepository repository = new NotificacionRepository();

// Obtener notificaciones de un usuario
repository.obtenerNotificacionesUsuario(usuarioId, callback);

// Marcar como leída
repository.marcarComoLeida(notificacionId, callback);

// Eliminar notificaciones de una cita
repository.eliminarNotificacionesCita(citaId, callback);
```

## 📱 Interfaz de Usuario

### Formulario de Actividad

- **Campo "Días de aviso previo"**: Permite configurar cuántos días antes notificar
- **Valor por defecto**: 1 día
- **Validación**: Solo números positivos

### Pantalla de Notificaciones

- **Lista de notificaciones**: Muestra todas las notificaciones del usuario
- **Indicador visual**: Punto azul para notificaciones no leídas
- **Estados**: Leída/No leída, con diferentes estilos visuales
- **Acciones**: Click para marcar como leída y navegar a la actividad

## 🔄 Procesamiento Automático

### Servicio en Segundo Plano

El `NotificationBackgroundService` se ejecuta cada 30 minutos para:

1. **Verificar notificaciones pendientes** en Firestore
2. **Enviar notificaciones push** cuando corresponde
3. **Marcar como procesadas** las notificaciones enviadas

### Inicio Automático

El `NotificationBootReceiver` asegura que el servicio se inicie:
- Al arrancar el dispositivo
- Al actualizar la aplicación
- Al reinstalar la aplicación

## 📊 Estructura de Datos

### Modelo Notificacion

```java
public class Notificacion {
    private String id;                    // ID único
    private String tipo;                  // "recordatorio", "cancelacion", "reagendamiento"
    private String citaId;                // ID de la cita (opcional)
    private String actividadNombre;       // Nombre de la actividad
    private Timestamp fecha;              // Fecha programada para enviar
    private String mensaje;               // Mensaje de la notificación
    private boolean leida;                // Estado leída/no leída
    private Timestamp fechaCreacion;      // Cuándo se creó
    private List<String> usuariosNotificados; // Lista de usuarios
}
```

### Colección Firestore

```
notificaciones/
├── {notificacionId}/
│   ├── id: string
│   ├── tipo: string
│   ├── citaId: string (opcional)
│   ├── actividadNombre: string
│   ├── fecha: timestamp
│   ├── mensaje: string
│   ├── leida: boolean
│   ├── fechaCreacion: timestamp
│   └── usuariosNotificados: array<string>
```

## 🧪 Ejemplos de Uso

### Ejemplo 1: Actividad Puntual

```java
// Actividad que se realiza una sola vez
Actividad actividad = new Actividad();
actividad.setNombre("Taller de Cocina");
actividad.setDiasAvisoPrevio(2); // Notificar 2 días antes
actividad.setPeriodicidad("Puntual");

// Las notificaciones se programarán cuando se cree la cita
```

### Ejemplo 2: Actividad Periódica

```java
// Actividad que se repite
Actividad actividad = new Actividad();
actividad.setNombre("Clases de Yoga");
actividad.setDiasAvisoPrevio(1); // Notificar 1 día antes
actividad.setPeriodicidad("Periodica");
actividad.setFechaInicio(timestampInicio);

// Se programa notificación basada en fechaInicio
```

### Ejemplo 3: Cita Específica

```java
// Cita individual con notificación personalizada
Cita cita = new Cita();
cita.setFecha(timestampFecha);
cita.setActividadNombre("Consulta Médica");

Actividad actividad = new Actividad();
actividad.setDiasAvisoPrevio(1);

// Se programa notificación específica para esta cita
```

## 🔍 Monitoreo y Debugging

### Logs del Sistema

```java
// Tags de logging utilizados
"NotificationService"        // Servicio principal
"NotificationBgService"     // Servicio en segundo plano
"NotificacionRepository"     // Operaciones de base de datos
"NOTIFICATIONS"             // Logs generales del sistema
```

### Verificación de Estado

```java
// Verificar notificaciones pendientes
notificacionRepository.obtenerNotificacionesPendientes(fechaLimite, result -> {
    if (result.isSuccess()) {
        List<Notificacion> pendientes = result.getData();
        Log.d("DEBUG", "Notificaciones pendientes: " + pendientes.size());
    }
});
```

## 🚨 Consideraciones Importantes

### Rendimiento

- **Procesamiento en lotes**: Las notificaciones se procesan en grupos
- **Intervalo de verificación**: Cada 30 minutos para optimizar batería
- **Límites de Firestore**: Respeta los límites de lectura/escritura

### Seguridad

- **Validación de usuarios**: Solo usuarios autorizados pueden recibir notificaciones
- **Permisos**: Requiere permisos de notificación en Android 13+
- **Datos sensibles**: Los mensajes no contienen información confidencial

### Compatibilidad

- **Android 8.0+**: Requiere canales de notificación
- **Firebase**: Compatible con Firestore y Firebase Auth
- **Material Design**: Sigue las guías de diseño de Material Design 3

## 🔮 Futuras Mejoras

### Funcionalidades Planificadas

- [ ] **Notificaciones Push Remotas**: Integración con FCM
- [ ] **Personalización**: Usuarios pueden configurar sus preferencias
- [ ] **Notificaciones por Email**: Envío de correos electrónicos
- [ ] **Analytics**: Métricas de entrega y apertura
- [ ] **Templates**: Plantillas personalizables de mensajes

### Optimizaciones

- [ ] **Cache Local**: Almacenamiento local para reducir consultas
- [ ] **Batch Processing**: Procesamiento más eficiente en lotes
- [ ] **Smart Scheduling**: Algoritmos inteligentes para horarios óptimos
- [ ] **Offline Support**: Funcionamiento sin conexión a internet

---

## 📞 Soporte

Para preguntas o problemas con el sistema de notificaciones, contactar al equipo de desarrollo del Centro Integral Alerce.

**Versión**: 1.0.0  
**Última actualización**: Diciembre 2024

