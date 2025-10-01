package com.centroalerce.auth;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.*;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;
import com.centroalerce.gestion.R;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
// Firebase (opcional si ya configuraste BoM)
// import com.google.firebase.auth.FirebaseAuth;

public class LoginFragment extends Fragment {

    private TextInputEditText etEmail, etPass;
    private MaterialButton btnLogin;
    // private FirebaseAuth auth;

    public LoginFragment(){}

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inf, @Nullable ViewGroup c, @Nullable Bundle b) {
        View v = inf.inflate(R.layout.fragment_login, c, false);

        etEmail = v.findViewById(R.id.etEmail);
        etPass  = v.findViewById(R.id.etPass);
        btnLogin= v.findViewById(R.id.btnLogin);
        TextView tvForgot = v.findViewById(R.id.tvForgot);
        MaterialButton btnCrear = v.findViewById(R.id.btnCrearCuenta);
        TextView tvCreateFromLink = v.findViewById(R.id.tvCreateFromLink);
        TextView tvCreateInlineLink = v.findViewById(R.id.tvCreateInlineLink);

        TextWatcher watcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                boolean enable = !isEmpty(etEmail) && !isEmpty(etPass);
                btnLogin.setEnabled(enable);
            }
            @Override public void afterTextChanged(Editable s) {}
        };
        etEmail.addTextChangedListener(watcher);
        etPass.addTextChangedListener(watcher);

        // auth = FirebaseAuth.getInstance();

        btnLogin.setOnClickListener(x -> doLogin(v));
        tvForgot.setOnClickListener(x -> doForgot());
        btnCrear.setOnClickListener(x -> openSignup(v));
        tvCreateInlineLink.setOnClickListener(x -> openSignup(v));

        return v;
    }

    private boolean isEmpty(TextInputEditText e){
        return e.getText()==null || e.getText().toString().trim().isEmpty();
    }

    private void doLogin(View root){
        String email = etEmail.getText().toString().trim();
        String pass  = etPass.getText().toString();

        // TODO: descomenta si tienes Firebase configurado:
        /*
        btnLogin.setEnabled(false);
        auth.signInWithEmailAndPassword(email, pass)
            .addOnCompleteListener(task -> {
                btnLogin.setEnabled(true);
                if (task.isSuccessful()) {
                    // Navegar a Home
                    Navigation.findNavController(root).navigate(R.id.homeFragment);
                } else {
                    Toast.makeText(getContext(), "Error: " +
                        task.getException().getMessage(), Toast.LENGTH_LONG).show();
                }
            });
        */
        // DEMO sin Firebase:
        Toast.makeText(getContext(),"Login demo", Toast.LENGTH_SHORT).show();
        Navigation.findNavController(root).navigate(R.id.homeFragment);
    }

    private void doForgot(){
        String email = etEmail.getText()==null ? "" : etEmail.getText().toString().trim();
        if (email.isEmpty()){
            Toast.makeText(getContext(),"Ingresa tu email para recuperar", Toast.LENGTH_SHORT).show();
            return;
        }
        // TODO: Firebase reset:
        // auth.sendPasswordResetEmail(email).addOnSuccessListener(v ->
        //   Toast.makeText(getContext(),"Email de recuperación enviado", Toast.LENGTH_LONG).show());

        Toast.makeText(getContext(),"(Demo) Se enviaría email de recuperación", Toast.LENGTH_SHORT).show();
    }

    private void openSignup(View root){
        // TODO: navegar a SignupFragment cuando lo tengas
        Toast.makeText(getContext(),"Abrir Crear cuenta (por implementar)", Toast.LENGTH_SHORT).show();
        // Navigation.findNavController(root).navigate(R.id.signupFragment);
    }
}
