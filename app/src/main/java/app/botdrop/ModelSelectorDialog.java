package app.botdrop;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.termux.R;
import com.termux.shared.logger.Logger;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Dialog for selecting a model with search capability.
 * Uses a static provider/model catalog bundled with the app.
 */
public class ModelSelectorDialog extends Dialog {

    private static final String LOG_TAG = "ModelSelectorDialog";
    private static final String STATIC_MODELS_ASSET = "openclaw-models-all.keys";

    // Loaded once per process. 711 entries is small enough to keep in memory.
    private static List<ModelInfo> sCachedAllModels;

    private ModelSelectedCallback mCallback;

    private EditText mSearchBox;
    private RecyclerView mModelList;
    private TextView mStatusText;
    private Button mRetryButton;

    private ModelListAdapter mAdapter;
    private List<ModelInfo> mAllModels = new ArrayList<>();

    public interface ModelSelectedCallback {
        void onModelSelected(String provider, String model);
    }

    public ModelSelectorDialog(@NonNull Context context, BotDropService service) {
        super(context);
    }

    public ModelSelectorDialog(@NonNull Context context, BotDropService service, boolean allowFallback) {
        super(context);
    }

    public void show(ModelSelectedCallback callback) {
        this.mCallback = callback;
        super.show();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.dialog_model_selector);

        Window window = getWindow();
        if (window != null) {
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            window.setBackgroundDrawableResource(android.R.color.transparent);
        }

        mSearchBox = findViewById(R.id.model_search);
        mModelList = findViewById(R.id.model_list);
        mStatusText = findViewById(R.id.model_status);
        mRetryButton = findViewById(R.id.model_retry);
        ImageButton closeButton = findViewById(R.id.model_close_button);

        closeButton.setOnClickListener(v -> {
            if (mCallback != null) {
                mCallback.onModelSelected(null, null);
            }
            dismiss();
        });

        mAdapter = new ModelListAdapter(model -> {
            if (mCallback != null) {
                mCallback.onModelSelected(model.provider, model.model);
            }
            dismiss();
        });
        mModelList.setLayoutManager(new LinearLayoutManager(getContext()));
        mModelList.setAdapter(mAdapter);

        mSearchBox.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterModels(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        mRetryButton.setOnClickListener(v -> loadModels());

        loadModels();
    }

    private void loadModels() {
        showLoading();

        if (sCachedAllModels == null || sCachedAllModels.isEmpty()) {
            long t0 = System.currentTimeMillis();
            sCachedAllModels = readModelsFromAsset();
            Logger.logInfo(LOG_TAG, "Static catalog loaded: " + sCachedAllModels.size() + " models in " +
                (System.currentTimeMillis() - t0) + "ms");
        }

        if (sCachedAllModels.isEmpty()) {
            showError("Failed to load model catalog.");
            return;
        }

        mAllModels = new ArrayList<>(sCachedAllModels);
        mAdapter.updateList(mAllModels);
        showList();
    }

    private List<ModelInfo> readModelsFromAsset() {
        List<ModelInfo> models = new ArrayList<>();

        try (InputStream is = getContext().getAssets().open(STATIC_MODELS_ASSET);
             BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String key = line.trim();
                if (isModelToken(key)) {
                    models.add(new ModelInfo(key));
                }
            }
        } catch (Exception e) {
            Logger.logError(LOG_TAG, "Failed to read static model catalog: " + e.getMessage());
        }

        return models;
    }

    private boolean isModelToken(String token) {
        if (token == null || token.isEmpty()) return false;
        if (!token.contains("/")) return false;
        return token.matches("[A-Za-z0-9._-]+/[A-Za-z0-9._:/-]+");
    }

    private void filterModels(String query) {
        if (query == null || query.isEmpty()) {
            mAdapter.updateList(mAllModels);
            return;
        }

        String lower = query.toLowerCase();
        List<ModelInfo> filtered = mAllModels.stream()
            .filter(m -> m.fullName.toLowerCase().contains(lower))
            .collect(Collectors.toList());
        mAdapter.updateList(filtered);
    }

    private void showLoading() {
        mModelList.setVisibility(View.GONE);
        mRetryButton.setVisibility(View.GONE);
        mStatusText.setVisibility(View.VISIBLE);
        mStatusText.setText("Loading models...");
    }

    private void showError(String message) {
        mModelList.setVisibility(View.GONE);
        mRetryButton.setVisibility(View.VISIBLE);
        mStatusText.setVisibility(View.VISIBLE);
        mStatusText.setText(message);
    }

    private void showList() {
        mModelList.setVisibility(View.VISIBLE);
        mRetryButton.setVisibility(View.GONE);
        mStatusText.setVisibility(View.GONE);
    }
}
