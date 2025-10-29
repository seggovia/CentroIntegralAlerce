package com.centroalerce.ui.mantenedores.dialog;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;   // <- IMPORT CLAVE

import com.centroalerce.gestion.R;
import com.centroalerce.gestion.models.Beneficiario;
import com.centroalerce.gestion.repositories.BeneficiarioRepository;
import com.centroalerce.ui.mantenedores.adapter.BeneficiarioCheckAdapter; // <- RUTA DE TU ADAPTER

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class BeneficiariosPickerSheet extends BottomSheetDialogFragment
        implements BeneficiarioCheckAdapter.OnSelectionChanged {

    public interface OnBeneficiariosSelectedListener {
        void onBeneficiariosSelected(List<Beneficiario> seleccionados);
    }

    private RecyclerView rv;
    private EditText etBuscar;
    private CheckBox chkSelectAll;
    private TextView tvContador;
    private Button btnListo;

    private BeneficiarioCheckAdapter adapter;
    private final BeneficiarioRepository repo = new BeneficiarioRepository();
    private final List<Beneficiario> backingData = new ArrayList<>();

    private OnBeneficiariosSelectedListener listener;

    public static BeneficiariosPickerSheet newInstance(Collection<String> preselectedIds) {
        BeneficiariosPickerSheet s = new BeneficiariosPickerSheet();
        if (preselectedIds != null) {
            Bundle b = new Bundle();
            b.putSerializable("pre", (Serializable) new ArrayList<>(preselectedIds));
            s.setArguments(b);
        }
        return s;
    }

    public void setListener(OnBeneficiariosSelectedListener l) { this.listener = l; }

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater i, @Nullable ViewGroup c, @Nullable Bundle b) {
        return i.inflate(R.layout.sheet_beneficiarios_picker, c, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(v, savedInstanceState);

        rv = v.findViewById(R.id.rvBeneficiarios);
        etBuscar = v.findViewById(R.id.etBuscar);
        chkSelectAll = v.findViewById(R.id.chkSelectAll);
        tvContador = v.findViewById(R.id.tvContador);
        btnListo = v.findViewById(R.id.btnListo);

        List<String> pre = null;
        if (getArguments() != null) {
            //noinspection unchecked
            pre = (List<String>) getArguments().getSerializable("pre");
        }

        adapter = new BeneficiarioCheckAdapter(this, pre);
        rv.setLayoutManager(new LinearLayoutManager(requireContext())); // <- ya compila
        rv.setAdapter(adapter);

        etBuscar.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) { adapter.filter(s.toString()); }
            @Override public void afterTextChanged(Editable s) {}
        });

        chkSelectAll.setOnCheckedChangeListener((btn, checked) -> adapter.selectAllVisible(checked));

        btnListo.setOnClickListener(v1 -> {
            if (listener != null) listener.onBeneficiariosSelected(adapter.getSelected(backingData));
            dismiss();
        });

        cargar();
    }

    private void cargar() {
        repo.getAll(new BeneficiarioRepository.ListCallback() {
            @Override public void onSuccess(List<Beneficiario> items) {
                backingData.clear();
                backingData.addAll(items);
                adapter.setData(items);
            }
            @Override public void onError(Exception e) {
                Toast.makeText(requireContext(), "Error cargando beneficiarios", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override public void onSelectionCountChanged(int count) {
        tvContador.setText(count + " seleccionados");
    }
}
