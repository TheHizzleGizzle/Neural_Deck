package com.example.wtfff.ui;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.example.wtfff.R;
import com.example.wtfff.ai.OpenAiCompatibleClient;
import com.example.wtfff.storage.AiConfig;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.util.Locale;

public class ByokSettingsSheet {

    public interface OnSavedListener {
        void onSaved();
    }

    public static void show(Context context,
                            AiConfig aiConfig,
                            OpenAiCompatibleClient aiClient,
                            OnSavedListener listener) {
        BottomSheetDialog dialog = new BottomSheetDialog(context);
        View root = LayoutInflater.from(context).inflate(R.layout.bottom_sheet_byok, null);
        dialog.setContentView(root);

        TextView chipOpenRouter = root.findViewById(R.id.chipProvOpenRouter);
        TextView chipOpenAi = root.findViewById(R.id.chipProvOpenAi);
        TextView chipGroq = root.findViewById(R.id.chipProvGroq);
        TextView chipDeepSeek = root.findViewById(R.id.chipProvDeepSeek);
        TextView chipCustom = root.findViewById(R.id.chipProvCustom);

        EditText etBaseUrl = root.findViewById(R.id.etBaseUrl);
        EditText etApiKey = root.findViewById(R.id.etApiKey);
        EditText etModelId = root.findViewById(R.id.etModelId);
        SeekBar seekTemp = root.findViewById(R.id.seekTemperature);
        TextView tvTempValue = root.findViewById(R.id.tvTempValue);
        EditText etGlobalContext = root.findViewById(R.id.etGlobalContext);
        TextView tvPingResult = root.findViewById(R.id.tvPingResult);
        TextView btnPing = root.findViewById(R.id.btnPingConnection);
        TextView btnSave = root.findViewById(R.id.btnSaveByok);

        final String[] selectedProvider = {aiConfig.getProviderId()};

        Runnable refreshProviderFields = () -> {
            String p = selectedProvider[0];
            styleChip(chipOpenRouter, AiConfig.PROVIDER_OPENROUTER.equals(p));
            styleChip(chipOpenAi, AiConfig.PROVIDER_OPENAI.equals(p));
            styleChip(chipGroq, AiConfig.PROVIDER_GROQ.equals(p));
            styleChip(chipDeepSeek, AiConfig.PROVIDER_DEEPSEEK.equals(p));
            styleChip(chipCustom, AiConfig.PROVIDER_CUSTOM.equals(p));

            aiConfig.setProviderId(p);
            etBaseUrl.setText(aiConfig.getBaseUrl());
            etModelId.setText(aiConfig.getModel());
            etApiKey.setText(aiConfig.getApiKeyForProvider(p));
            tvPingResult.setVisibility(View.GONE);
        };

        chipOpenRouter.setOnClickListener(v -> {
            selectedProvider[0] = AiConfig.PROVIDER_OPENROUTER;
            refreshProviderFields.run();
        });
        chipOpenAi.setOnClickListener(v -> {
            selectedProvider[0] = AiConfig.PROVIDER_OPENAI;
            refreshProviderFields.run();
        });
        chipGroq.setOnClickListener(v -> {
            selectedProvider[0] = AiConfig.PROVIDER_GROQ;
            refreshProviderFields.run();
        });
        chipDeepSeek.setOnClickListener(v -> {
            selectedProvider[0] = AiConfig.PROVIDER_DEEPSEEK;
            refreshProviderFields.run();
        });
        chipCustom.setOnClickListener(v -> {
            selectedProvider[0] = AiConfig.PROVIDER_CUSTOM;
            refreshProviderFields.run();
        });

        int initialProgress = Math.round(aiConfig.getTemperature() * 100f);
        seekTemp.setProgress(initialProgress);
        tvTempValue.setText(String.format(Locale.US, "%.2f", aiConfig.getTemperature()));
        seekTemp.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                tvTempValue.setText(String.format(Locale.US, "%.2f", progress / 100f));
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        etGlobalContext.setText(aiConfig.getGlobalContext());
        refreshProviderFields.run();

        btnPing.setOnClickListener(v -> {
            String url = etBaseUrl.getText().toString().trim();
            String key = etApiKey.getText().toString().trim();
            String model = etModelId.getText().toString().trim();
            tvPingResult.setVisibility(View.VISIBLE);
            tvPingResult.setTextColor(0xFF00F0FF);
            tvPingResult.setText("PINGING_ENDPOINT // " + url + " ...");
            btnPing.setEnabled(false);

            aiClient.testConnection(url, key, model, new OpenAiCompatibleClient.Callback<String>() {
                @Override
                public void onSuccess(String result) {
                    btnPing.setEnabled(true);
                    tvPingResult.setTextColor(0xFF39FF88);
                    tvPingResult.setText(result);
                }

                @Override
                public void onError(String errorMessage) {
                    btnPing.setEnabled(true);
                    tvPingResult.setTextColor(0xFFFF3864);
                    tvPingResult.setText("LINK_ERROR // " + errorMessage);
                }
            });
        });

        btnSave.setOnClickListener(v -> {
            String p = selectedProvider[0];
            String url = etBaseUrl.getText().toString().trim();
            String key = etApiKey.getText().toString().trim();
            String model = etModelId.getText().toString().trim();
            float temp = seekTemp.getProgress() / 100f;
            String globalCtx = etGlobalContext.getText().toString().trim();

            aiConfig.setProviderId(p);
            aiConfig.setBaseUrl(p, url.isEmpty() ? AiConfig.defaultBaseUrl(p) : url);
            aiConfig.setModel(p, model.isEmpty() ? AiConfig.defaultModel(p) : model);
            aiConfig.saveApiKeyForProvider(p, key);
            aiConfig.setTemperature(temp);
            aiConfig.setGlobalContext(globalCtx);

            Toast.makeText(context, "BYOK Vault updated [" + aiConfig.getProviderLabel(p) + "]", Toast.LENGTH_SHORT).show();
            if (listener != null) listener.onSaved();
            dialog.dismiss();
        });

        dialog.show();
    }

    private static void styleChip(TextView chip, boolean active) {
        chip.setBackgroundResource(active ? R.drawable.bg_px_chip_active : R.drawable.bg_px_chip_idle);
        chip.setTextColor(active ? 0xFF00F0FF : 0xFF8DA4C7);
    }
}
