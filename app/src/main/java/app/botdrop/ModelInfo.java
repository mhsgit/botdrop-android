package app.botdrop;

/**
 * Model information parsed from openclaw models list.
 */
public class ModelInfo {

    public String fullName;     // "google/gemini-3-flash-preview"
    public String provider;     // "google"
    public String model;        // "gemini-3-flash-preview"
    public String input;        // "text+image"
    public String context;      // "1024k"
    public boolean isDefault;   // has "default" tag
    public String statusText;   // Optional label for provider rows (e.g. configured/unconfigured)
    public boolean isSectionHeader;

    public ModelInfo(String fullName) {
        this.fullName = fullName;

        // Parse provider and model from fullName
        if (fullName != null && fullName.contains("/")) {
            String[] parts = fullName.split("/", 2);
            this.provider = parts[0];
            this.model = parts[1];
        }
    }

    public ModelInfo(String fullName, String provider, String model) {
        this.fullName = fullName;
        this.provider = provider;
        this.model = model;
    }

    @Override
    public String toString() {
        return fullName;
    }
}
