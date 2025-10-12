package com.centroalerce.gestion;

import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import com.centroalerce.gestion.utils.MigracionRoles;
import java.util.Map;

/**
 * Activity temporal para ejecutar la migración de roles
 * BORRAR DESPUÉS DE EJECUTAR LA MIGRACIÓN
 */
public class MigrationActivity extends AppCompatActivity {

    private MigracionRoles migracionRoles;
    private TextView tvStatus;
    private Button btnMigrar;
    private Button btnVerificar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_migration);

        migracionRoles = new MigracionRoles();

        tvStatus = findViewById(R.id.tvMigrationStatus);
        btnMigrar = findViewById(R.id.btnMigrate);
        btnVerificar = findViewById(R.id.btnVerify);

        btnMigrar.setOnClickListener(v -> mostrarDialogoConfirmacion());
        btnVerificar.setOnClickListener(v -> verificarRoles());

        tvStatus.setText("⚠️ ADVERTENCIA: Esta migración asignará el rol de ADMINISTRADOR a TODOS los usuarios existentes.\n\nEjecuta esto SOLO UNA VEZ.");
    }

    private void mostrarDialogoConfirmacion() {
        new AlertDialog.Builder(this)
                .setTitle("⚠️ Confirmar Migración")
                .setMessage("¿Estás seguro de que quieres asignar el rol de ADMINISTRADOR a TODOS los usuarios existentes?\n\nEsta acción no se puede deshacer fácilmente.")
                .setPositiveButton("Sí, migrar", (dialog, which) -> ejecutarMigracion())
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void ejecutarMigracion() {
        btnMigrar.setEnabled(false);
        tvStatus.setText("🔄 Migrando usuarios...\nPor favor espera...");

        migracionRoles.asignarAdministradorATodos(new MigracionRoles.OnMigrationListener() {
            @Override
            public void onComplete(int totalProcesados, int totalActualizados) {
                runOnUiThread(() -> {
                    String mensaje = String.format(
                            "✅ MIGRACIÓN COMPLETADA\n\n" +
                                    "Total de usuarios procesados: %d\n" +
                                    "Total de usuarios actualizados: %d\n\n" +
                                    "Todos los usuarios ahora son ADMINISTRADORES.",
                            totalProcesados, totalActualizados
                    );

                    tvStatus.setText(mensaje);
                    btnMigrar.setText("Migración Completada ✓");
                    Toast.makeText(MigrationActivity.this,
                            "Migración exitosa", Toast.LENGTH_LONG).show();

                    // Mostrar diálogo de éxito
                    new AlertDialog.Builder(MigrationActivity.this)
                            .setTitle("✅ Migración Exitosa")
                            .setMessage(mensaje + "\n\n⚠️ IMPORTANTE: Ahora puedes BORRAR esta Activity del proyecto.")
                            .setPositiveButton("Entendido", null)
                            .show();
                });
            }

            @Override
            public void onError(Exception e) {
                runOnUiThread(() -> {
                    String mensaje = "❌ ERROR en la migración:\n" + e.getMessage();
                    tvStatus.setText(mensaje);
                    btnMigrar.setEnabled(true);
                    Toast.makeText(MigrationActivity.this,
                            "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void verificarRoles() {
        tvStatus.setText("🔍 Verificando roles...");

        migracionRoles.verificarRoles(new MigracionRoles.OnVerificationListener() {
            @Override
            public void onVerificationComplete(Map<String, Integer> conteoRoles) {
                runOnUiThread(() -> {
                    String mensaje = "📊 ESTADO ACTUAL DE ROLES:\n\n" +
                            "Administradores: " + conteoRoles.get("administrador") + "\n" +
                            "Usuarios: " + conteoRoles.get("usuario") + "\n" +
                            "Visualizadores: " + conteoRoles.get("visualizador") + "\n" +
                            "Sin rol: " + conteoRoles.get("sin_rol");

                    tvStatus.setText(mensaje);
                    Toast.makeText(MigrationActivity.this,
                            "Verificación completada", Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onVerificationError(Exception e) {
                runOnUiThread(() -> {
                    tvStatus.setText("❌ Error al verificar: " + e.getMessage());
                    Toast.makeText(MigrationActivity.this,
                            "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
    }
}