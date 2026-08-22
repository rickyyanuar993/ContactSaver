package com.contactsaver;

import android.Manifest;
import android.accounts.AccountManager;
import android.app.AlertDialog;
import android.content.ContentProviderOperation;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.ContactsContract;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_PERMISSIONS = 100;
    private static final int PICK_FILE = 200;
    private static final String PREFS_NAME = "ContactSaverPrefs";

    // Source type constants
    static final String SRC_GOOGLE   = "Google Account";
    static final String SRC_PHONE    = "Memori HP";
    static final String SRC_SIM      = "SIM Card";
    static final String SRC_WA       = "WhatsApp";
    static final String SRC_WA_BIZ   = "WhatsApp Business";
    static final String SRC_OTHER    = "Lainnya";

    // Pages
    private LinearLayout layoutMain, layoutStats, layoutBackup, layoutSettings;

    // Main page
    private Button btnPickFile, btnAnalyze, btnProcess, btnGoStats, btnGoBackup, btnGoSettings, btnPreviewImport;
    private TextView tvFileName, tvStatus, tvResult, tvAnalyzeStatus, tvAnalyzeResult, tvSaveDestNote;
    private ProgressBar progressBar, progressAnalyze;
    private LinearLayout layoutResult, layoutAnalyzeResult, layoutGoogleAccountPicker;
    private CheckBox chkAnalyzeGoogle, chkAnalyzePhone, chkAnalyzeSim, chkAnalyzeWa, chkAnalyzeWaBiz;
    private android.widget.RadioGroup rgSaveDestination;
    private android.widget.RadioButton rbSavePhone, rbSaveGoogle;
    private Spinner spinnerGoogleAccount;
    private List<android.accounts.Account> googleAccounts = new ArrayList<>();

    // Stats page
    private TextView tvStatsTotal, tvStatsDuplicate, tvStatsUnique, tvStatsSource, tvStatsStatus;
    private Button btnDeleteDuplicates, btnDeleteAll, btnBackupFilterAll, btnBackupFilterUnique, btnBackFromStats, btnApplyFilter, btnViewContacts;
    private ProgressBar progressStats;
    private CheckBox chkGoogle, chkPhone, chkSim, chkWa, chkWaBusiness, chkOther;

    // Filtered contacts list (for "Lihat Kontak" dialog)
    private List<PhoneContact> lastFilteredContacts = new ArrayList<>();

    // Backup page
    private TextView tvBackupInfo, tvBackupStatus;
    private Button btnBackupAll, btnBackupUnique, btnBackFromBackup;
    private ProgressBar progressBackup;

    // Settings page
    private Button btnBackFromSettings, btnSaveSettings;
    private TextView tvSettingsStatus;
    private Spinner spinnerPriority1, spinnerPriority2, spinnerPriority3, spinnerPriority4, spinnerPriority5;

    private Uri selectedFileUri = null;
    private List<ContactEntry> analyzedToSave = null; // hasil analisis langkah 2
    private List<ContactEntry> allParsedContacts = null;
    private Set<String> existingPhonesForPreview = null;
    private String detectedFormat = "";

    private ExecutorService executor = Executors.newSingleThreadExecutor();
    private Handler mainHandler = new Handler(Looper.getMainLooper());
    private List<DuplicateGroup> duplicateGroups = new ArrayList<>();

    private SharedPreferences prefs;

    // Priority order (index 0 = highest priority = dipertahankan)
    private List<String> priorityOrder = new ArrayList<>(Arrays.asList(
        SRC_GOOGLE, SRC_PHONE, SRC_SIM, SRC_WA_BIZ, SRC_OTHER
    ));

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        // Pages
        layoutMain     = findViewById(R.id.layoutMain);
        layoutStats    = findViewById(R.id.layoutStats);
        layoutBackup   = findViewById(R.id.layoutBackup);
        layoutSettings = findViewById(R.id.layoutSettings);

        // Main
        btnPickFile          = findViewById(R.id.btnPickFile);
        btnAnalyze           = findViewById(R.id.btnAnalyze);
        btnProcess           = findViewById(R.id.btnProcess);
        btnPreviewImport     = findViewById(R.id.btnPreviewImport);
        btnGoStats           = findViewById(R.id.btnGoStats);
        btnGoBackup          = findViewById(R.id.btnGoBackup);
        btnGoSettings        = findViewById(R.id.btnGoSettings);
        tvFileName           = findViewById(R.id.tvFileName);
        tvStatus             = findViewById(R.id.tvStatus);
        tvResult             = findViewById(R.id.tvResult);
        tvAnalyzeStatus      = findViewById(R.id.tvAnalyzeStatus);
        tvAnalyzeResult      = findViewById(R.id.tvAnalyzeResult);
        tvSaveDestNote       = findViewById(R.id.tvSaveDestNote);
        progressBar          = findViewById(R.id.progressBar);
        progressAnalyze      = findViewById(R.id.progressAnalyze);
        layoutResult         = findViewById(R.id.layoutResult);
        layoutAnalyzeResult  = findViewById(R.id.layoutAnalyzeResult);
        layoutGoogleAccountPicker = findViewById(R.id.layoutGoogleAccountPicker);
        chkAnalyzeGoogle     = findViewById(R.id.chkAnalyzeGoogle);
        chkAnalyzePhone      = findViewById(R.id.chkAnalyzePhone);
        chkAnalyzeSim        = findViewById(R.id.chkAnalyzeSim);
        chkAnalyzeWa         = findViewById(R.id.chkAnalyzeWa);
        chkAnalyzeWaBiz      = findViewById(R.id.chkAnalyzeWaBiz);
        rgSaveDestination    = findViewById(R.id.rgSaveDestination);
        rbSavePhone          = findViewById(R.id.rbSavePhone);
        rbSaveGoogle         = findViewById(R.id.rbSaveGoogle);
        spinnerGoogleAccount = findViewById(R.id.spinnerGoogleAccount);

        // Stats
        tvStatsTotal         = findViewById(R.id.tvStatsTotal);
        tvStatsDuplicate     = findViewById(R.id.tvStatsDuplicate);
        tvStatsUnique        = findViewById(R.id.tvStatsUnique);
        tvStatsSource        = findViewById(R.id.tvStatsSource);
        btnDeleteDuplicates  = findViewById(R.id.btnDeleteDuplicates);
        btnDeleteAll         = findViewById(R.id.btnDeleteAll);
        btnBackupFilterAll   = findViewById(R.id.btnBackupFilterAll);
        btnBackupFilterUnique= findViewById(R.id.btnBackupFilterUnique);
        btnBackFromStats     = findViewById(R.id.btnBackFromStats);
        progressStats        = findViewById(R.id.progressStats);
        tvStatsStatus        = findViewById(R.id.tvStatsStatus);
        btnApplyFilter       = findViewById(R.id.btnApplyFilter);
        btnViewContacts      = findViewById(R.id.btnViewContacts);
        chkGoogle            = findViewById(R.id.chkGoogle);
        chkPhone             = findViewById(R.id.chkPhone);
        chkSim               = findViewById(R.id.chkSim);
        chkWa                = findViewById(R.id.chkWa);
        chkWaBusiness        = findViewById(R.id.chkWaBusiness);
        chkOther             = findViewById(R.id.chkOther);

        // Backup
        tvBackupInfo         = findViewById(R.id.tvBackupInfo);
        tvBackupStatus       = findViewById(R.id.tvBackupStatus);
        btnBackupAll         = findViewById(R.id.btnBackupAll);
        btnBackupUnique      = findViewById(R.id.btnBackupUnique);
        btnBackFromBackup    = findViewById(R.id.btnBackFromBackup);
        progressBackup       = findViewById(R.id.progressBackup);

        // Settings
        btnBackFromSettings  = findViewById(R.id.btnBackFromSettings);
        btnSaveSettings      = findViewById(R.id.btnSaveSettings);
        tvSettingsStatus     = findViewById(R.id.tvSettingsStatus);
        spinnerPriority1     = findViewById(R.id.spinnerPriority1);
        spinnerPriority2     = findViewById(R.id.spinnerPriority2);
        spinnerPriority3     = findViewById(R.id.spinnerPriority3);
        spinnerPriority4     = findViewById(R.id.spinnerPriority4);
        spinnerPriority5     = findViewById(R.id.spinnerPriority5);

        setupSpinners();
        loadSettings();
        loadGoogleAccounts();

        // Save destination toggle
        rgSaveDestination.setOnCheckedChangeListener((group, checkedId) -> {
            boolean isGoogle = (checkedId == R.id.rbSaveGoogle);
            layoutGoogleAccountPicker.setVisibility(isGoogle ? View.VISIBLE : View.GONE);
            tvSaveDestNote.setText(isGoogle
                ? "⚠️ Kontak akan sync ke Google — pastikan kamu pilih akun yang benar."
                : "💡 Kontak tersimpan lokal di HP, tidak otomatis sync ke Google.");
        });

        // Listeners Main
        btnPickFile.setOnClickListener(v -> pickFile());
        btnAnalyze.setOnClickListener(v -> startAnalyze());
        btnProcess.setOnClickListener(v -> startProcessing());
        btnPreviewImport.setOnClickListener(v -> showImportPreviewDialog());
        btnGoStats.setOnClickListener(v -> openStatsPage());
        btnGoBackup.setOnClickListener(v -> openBackupPage());
        btnGoSettings.setOnClickListener(v -> openSettingsPage());

        // Listeners Stats
        btnBackFromStats.setOnClickListener(v -> showPage(layoutMain));
        btnDeleteDuplicates.setOnClickListener(v -> confirmDeleteDuplicates());
        btnDeleteAll.setOnClickListener(v -> confirmDeleteAll());
        btnBackupFilterAll.setOnClickListener(v -> startBackupFiltered(false));
        btnBackupFilterUnique.setOnClickListener(v -> startBackupFiltered(true));
        btnApplyFilter.setOnClickListener(v -> calculateStats());
        btnViewContacts.setOnClickListener(v -> showContactListDialog());

        // Listeners Backup
        btnBackFromBackup.setOnClickListener(v -> showPage(layoutMain));
        btnBackupAll.setOnClickListener(v -> startBackup(false));
        btnBackupUnique.setOnClickListener(v -> startBackup(true));

        // Listeners Settings
        btnBackFromSettings.setOnClickListener(v -> showPage(layoutMain));
        btnSaveSettings.setOnClickListener(v -> saveSettings());

        requestPermissions();
    }

    // ─── SETTINGS ────────────────────────────────────────────────────────────────

    private final String[] SOURCE_OPTIONS = {SRC_GOOGLE, SRC_PHONE, SRC_SIM, SRC_WA, SRC_WA_BIZ, SRC_OTHER};

    private void setupSpinners() {
        Spinner[] spinners = {spinnerPriority1, spinnerPriority2, spinnerPriority3, spinnerPriority4, spinnerPriority5};
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, SOURCE_OPTIONS) {
            @Override public View getView(int pos, View cv, android.view.ViewGroup parent) {
                View v = super.getView(pos, cv, parent); ((TextView)v).setTextColor(0xFFE2E8F0); ((TextView)v).setTextSize(14); return v;
            }
            @Override public View getDropDownView(int pos, View cv, android.view.ViewGroup parent) {
                View v = super.getDropDownView(pos, cv, parent); ((TextView)v).setTextColor(0xFFE2E8F0); ((TextView)v).setBackgroundColor(0xFF1E293B); return v;
            }
        };
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        for (Spinner s : spinners) s.setAdapter(adapter);
    }

    private void loadSettings() {
        // Load priority order
        for (int i = 0; i < 5; i++) {
            String val = prefs.getString("priority_" + i, SOURCE_OPTIONS[i]);
            int idx = Arrays.asList(SOURCE_OPTIONS).indexOf(val);
            if (idx < 0) idx = i;
            getSpinner(i).setSelection(idx);
        }
        // Build priorityOrder
        buildPriorityOrderFromSpinners();
    }

    private void saveSettings() {
        // Validate: no duplicates
        Set<String> chosen = new HashSet<>();
        for (int i = 0; i < 5; i++) {
            String val = (String) getSpinner(i).getSelectedItem();
            if (!chosen.add(val)) {
                tvSettingsStatus.setText("❌ Ada sumber yang sama di dua urutan! Pastikan semua berbeda.");
                tvSettingsStatus.setTextColor(0xFFEF4444);
                return;
            }
        }
        SharedPreferences.Editor ed = prefs.edit();
        for (int i = 0; i < 5; i++) ed.putString("priority_" + i, (String) getSpinner(i).getSelectedItem());
        ed.apply();
        buildPriorityOrderFromSpinners();
        tvSettingsStatus.setText("✅ Pengaturan disimpan!");
        tvSettingsStatus.setTextColor(0xFF34D399);
    }

    private void buildPriorityOrderFromSpinners() {
        priorityOrder.clear();
        for (int i = 0; i < 5; i++) priorityOrder.add((String) getSpinner(i).getSelectedItem());
    }

    private Spinner getSpinner(int i) {
        switch(i) {
            case 0: return spinnerPriority1;
            case 1: return spinnerPriority2;
            case 2: return spinnerPriority3;
            case 3: return spinnerPriority4;
            default: return spinnerPriority5;
        }
    }

    // ─── PAGES ───────────────────────────────────────────────────────────────────

    private void showPage(LinearLayout page) {
        layoutMain.setVisibility(View.GONE);
        layoutStats.setVisibility(View.GONE);
        layoutBackup.setVisibility(View.GONE);
        layoutSettings.setVisibility(View.GONE);
        page.setVisibility(View.VISIBLE);
    }

    @Override
    public void onBackPressed() {
        if ((layoutStats != null && layoutStats.getVisibility() == View.VISIBLE)
                || (layoutBackup != null && layoutBackup.getVisibility() == View.VISIBLE)
                || (layoutSettings != null && layoutSettings.getVisibility() == View.VISIBLE)) {
            showPage(layoutMain);
        } else {
            super.onBackPressed();
        }
    }

    private void openSettingsPage() {
        showPage(layoutSettings);
        tvSettingsStatus.setText("");
    }

    private void loadGoogleAccounts() {
        try {
            android.accounts.Account[] accounts = AccountManager.get(this)
                .getAccountsByType("com.google");
            googleAccounts.clear();
            googleAccounts.addAll(Arrays.asList(accounts));

            if (googleAccounts.isEmpty()) {
                rbSaveGoogle.setEnabled(false);
                rbSaveGoogle.setText("☁️ Google Account (tidak ada akun Google di HP)");
            } else {
                String[] names = new String[googleAccounts.size()];
                for (int i = 0; i < googleAccounts.size(); i++) names[i] = googleAccounts.get(i).name;
                ArrayAdapter<String> adapter = new ArrayAdapter<String>(this,
                        android.R.layout.simple_spinner_item, names) {
                    @Override public View getView(int pos, View cv, android.view.ViewGroup p) {
                        View v = super.getView(pos, cv, p);
                        ((TextView)v).setTextColor(0xFFE2E8F0); ((TextView)v).setTextSize(13); return v;
                    }
                    @Override public View getDropDownView(int pos, View cv, android.view.ViewGroup p) {
                        View v = super.getDropDownView(pos, cv, p);
                        ((TextView)v).setTextColor(0xFFE2E8F0); ((TextView)v).setBackgroundColor(0xFF1E293B); return v;
                    }
                };
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                spinnerGoogleAccount.setAdapter(adapter);
            }
        } catch (Exception e) {
            rbSaveGoogle.setEnabled(false);
        }
    }

    private void requestPermissions() {
        List<String> needed = new ArrayList<>();
        String[] perms = {
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.WRITE_CONTACTS,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        };
        for (String p : perms) {
            if (ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED)
                needed.add(p);
        }
        if (!needed.isEmpty())
            ActivityCompat.requestPermissions(this, needed.toArray(new String[0]), REQUEST_PERMISSIONS);
    }

    private void pickFile() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(Intent.createChooser(intent, "Pilih File Kontak"), PICK_FILE);
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == PICK_FILE && res == RESULT_OK && data != null) {
            selectedFileUri = data.getData();
            tvFileName.setText("📄 " + getFileName(selectedFileUri));
            // Reset langkah 2 & 3
            btnAnalyze.setEnabled(true);
            btnProcess.setEnabled(false);
            analyzedToSave = null;
            allParsedContacts = null;
            existingPhonesForPreview = null;
            detectedFormat = "";
            layoutAnalyzeResult.setVisibility(View.GONE);
            layoutResult.setVisibility(View.GONE);
            tvAnalyzeStatus.setText("File dipilih. Tap 'Analisis File' untuk mengecek.");
            tvStatus.setText("Selesaikan langkah 2 dulu.");
        }
    }

    private String getFileName(Uri uri) {
        String result = uri.getLastPathSegment();
        if (result != null && result.contains("/")) result = result.substring(result.lastIndexOf("/") + 1);
        return result != null ? result : "file_kontak";
    }

    // ─── LANGKAH 2: ANALISIS ─────────────────────────────────────────────────────

    private void startAnalyze() {
        if (selectedFileUri == null) { Toast.makeText(this, "Pilih file dulu!", Toast.LENGTH_SHORT).show(); return; }
        btnAnalyze.setEnabled(false);
        btnProcess.setEnabled(false);
        progressAnalyze.setVisibility(View.VISIBLE);
        layoutAnalyzeResult.setVisibility(View.GONE);
        tvAnalyzeStatus.setText("⏳ Membaca kontak di HP...");

        final boolean inclGoogle = chkAnalyzeGoogle.isChecked();
        final boolean inclPhone  = chkAnalyzePhone.isChecked();
        final boolean inclSim    = chkAnalyzeSim.isChecked();
        final boolean inclWa     = chkAnalyzeWa.isChecked();
        final boolean inclWaBiz  = chkAnalyzeWaBiz.isChecked();

        executor.execute(() -> {
            try {
                // Baca semua kontak lalu filter sesuai pilihan user
                List<PhoneContact> allContacts = getAllContactsWithSource();
                Set<String> existing = new HashSet<>();
                for (PhoneContact c : allContacts) {
                    String src = resolveSource(c.accountType);
                    if (src.equals(SRC_GOOGLE)  && !inclGoogle) continue;
                    if (src.equals(SRC_PHONE)   && !inclPhone)  continue;
                    if (src.equals(SRC_SIM)     && !inclSim)    continue;
                    if (src.equals(SRC_WA)      && !inclWa)     continue;
                    if (src.equals(SRC_WA_BIZ)  && !inclWaBiz)  continue;
                    if (c.phone != null) existing.add(normalizePhone(c.phone));
                }

                // Build label sumber yang dicek
                List<String> srcChecked = new ArrayList<>();
                if (inclGoogle) srcChecked.add("Google");
                if (inclPhone)  srcChecked.add("HP");
                if (inclSim)    srcChecked.add("SIM");
                if (inclWa)     srcChecked.add("WA");
                if (inclWaBiz)  srcChecked.add("WA Bisnis");
                final String srcLabel = srcChecked.isEmpty() ? "tidak ada" : String.join(", ", srcChecked);

                mainHandler.post(() -> tvAnalyzeStatus.setText("⏳ Membaca file..."));
                String fn = getFileName(selectedFileUri).toLowerCase();
                boolean isVcf = fn.endsWith(".vcf") || fn.endsWith(".vcard");
                if (isVcf) {
                    detectedFormat = "VCF (vCard) File";
                }
                List<ContactEntry> fileContacts = isVcf
                    ? parseVcf(selectedFileUri) : parseCsv(selectedFileUri);

                mainHandler.post(() -> tvAnalyzeStatus.setText("⏳ Menganalisis duplikat..."));
                List<ContactEntry> toSave = new ArrayList<>();
                List<ContactEntry> dupList = new ArrayList<>();
                for (ContactEntry c : fileContacts) {
                    boolean dup = false;
                    for (String p : c.phones) { if (existing.contains(normalizePhone(p))) { dup = true; break; } }
                    if (dup) dupList.add(c); else toSave.add(c);
                }

                final int total = fileContacts.size();
                final int dupCount = dupList.size();
                final int uniqueCount = toSave.size();
                analyzedToSave = toSave;
                allParsedContacts = fileContacts;
                existingPhonesForPreview = existing;

                mainHandler.post(() -> {
                    progressAnalyze.setVisibility(View.GONE);
                    btnAnalyze.setEnabled(true);
                    btnProcess.setEnabled(uniqueCount > 0);
                    btnPreviewImport.setEnabled(total > 0);
                    tvAnalyzeStatus.setText("✅ Analisis selesai!");
                    layoutAnalyzeResult.setVisibility(View.VISIBLE);
                    tvAnalyzeResult.setText(
                        "📊 HASIL ANALISIS FILE\n\n" +
                        "📁 Nama File              : " + getFileName(selectedFileUri) + "\n" +
                        "📝 Format Terdeteksi       : " + detectedFormat + "\n" +
                        "📁 Total kontak di file   : " + total + " kontak\n" +
                        "🔍 Dicek duplikat dari    : " + srcLabel + "\n" +
                        "🔁 Sudah ada (skip)       : " + dupCount + " kontak\n" +
                        "✨ Baru & unik             : " + uniqueCount + " kontak\n\n" +
                        (uniqueCount > 0
                            ? "➡️ Langkah 3: Pilih tujuan simpan\n   lalu tap 'Simpan Kontak Unik'."
                            : "ℹ️ Semua kontak di file sudah ada di HP.")
                    );
                    if (uniqueCount == 0) tvStatus.setText("ℹ️ Tidak ada kontak baru untuk disimpan.");
                    else tvStatus.setText("Siap menyimpan " + uniqueCount + " kontak unik.");
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    progressAnalyze.setVisibility(View.GONE);
                    btnAnalyze.setEnabled(true);
                    tvAnalyzeStatus.setText("❌ Error: " + e.getMessage());
                });
            }
        });
    }

    // ─── LANGKAH 3: SIMPAN ───────────────────────────────────────────────────────

    private void startProcessing() {
        if (analyzedToSave == null || analyzedToSave.isEmpty()) {
            Toast.makeText(this, "Lakukan analisis dulu!", Toast.LENGTH_SHORT).show(); return;
        }

        // Tentukan tujuan simpan
        final String accountType;
        final String accountName;
        final String destLabel;
        if (rbSaveGoogle.isChecked()) {
            if (googleAccounts.isEmpty()) {
                Toast.makeText(this, "Tidak ada akun Google!", Toast.LENGTH_SHORT).show(); return;
            }
            int sel = spinnerGoogleAccount.getSelectedItemPosition();
            android.accounts.Account acc = googleAccounts.get(sel < 0 ? 0 : sel);
            accountType = acc.type;       // "com.google"
            accountName = acc.name;       // email
            destLabel   = "Google (" + acc.name + ")";
        } else {
            accountType = null;
            accountName = null;
            destLabel   = "Memori HP";
        }

        btnProcess.setEnabled(false);
        btnAnalyze.setEnabled(false);
        progressBar.setVisibility(View.VISIBLE);
        tvStatus.setText("⏳ Menyimpan " + analyzedToSave.size() + " kontak ke " + destLabel + "...");
        layoutResult.setVisibility(View.GONE);

        final List<ContactEntry> toSave = new ArrayList<>(analyzedToSave);
        executor.execute(() -> {
            try {
                int saved = saveContactsBatch(toSave, accountType, accountName);
                mainHandler.post(() -> {
                    progressBar.setVisibility(View.GONE);
                    btnProcess.setEnabled(false);
                    btnAnalyze.setEnabled(true);
                    analyzedToSave = null;
                    tvStatus.setText("✅ Selesai!");
                    layoutResult.setVisibility(View.VISIBLE);
                    tvResult.setText(
                        "📊 HASIL SIMPAN\n\n" +
                        "📂 Disimpan ke         : " + destLabel + "\n" +
                        "✨ Kontak unik          : " + toSave.size() + " kontak\n" +
                        "💾 Berhasil disimpan   : " + saved + " kontak\n" +
                        (saved < toSave.size() ? "⚠️ Gagal simpan       : " + (toSave.size() - saved) + " kontak" : "")
                    );
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    progressBar.setVisibility(View.GONE);
                    btnProcess.setEnabled(true);
                    btnAnalyze.setEnabled(true);
                    tvStatus.setText("❌ Error: " + e.getMessage());
                });
            }
        });
    }

    // ─── STATS PAGE ──────────────────────────────────────────────────────────────

    private void openStatsPage() {
        showPage(layoutStats);
        tvStatsTotal.setText("📱 Total Kontak: -");
        tvStatsUnique.setText("✨ Unik: -");
        tvStatsDuplicate.setText("🔁 Duplikat: -");
        tvStatsSource.setText("📂 Sumber Kontak:\n💡 Pilih sumber filter di atas, lalu tap 'Terapkan Filter & Hitung Ulang'.");
        tvStatsStatus.setText("Tap 'Terapkan Filter & Hitung Ulang' untuk memulai.");
        progressStats.setVisibility(View.GONE);
        btnDeleteDuplicates.setEnabled(false);
        btnDeleteAll.setEnabled(false);
        btnBackupFilterAll.setEnabled(false);
        btnBackupFilterUnique.setEnabled(false);
        btnViewContacts.setEnabled(false);
    }

    private void calculateStats() {
        tvStatsTotal.setText("⏳ Menghitung...");
        tvStatsDuplicate.setText("⏳");
        tvStatsUnique.setText("⏳");
        tvStatsSource.setText("⏳ Memuat sumber...");
        tvStatsStatus.setText("");
        btnDeleteDuplicates.setEnabled(false);
        btnDeleteAll.setEnabled(false);
        btnBackupFilterAll.setEnabled(false);
        btnBackupFilterUnique.setEnabled(false);
        btnViewContacts.setEnabled(false);
        progressStats.setVisibility(View.VISIBLE);

        // Baca filter checkbox
        final boolean inclGoogle  = chkGoogle.isChecked();
        final boolean inclPhone   = chkPhone.isChecked();
        final boolean inclSim     = chkSim.isChecked();
        final boolean inclWa      = chkWa.isChecked();
        final boolean inclWaBiz   = chkWaBusiness.isChecked();
        final boolean inclOther   = chkOther.isChecked();

        executor.execute(() -> {
            try {
                List<PhoneContact> all = getAllContactsWithSource();

                // Apply filter
                List<PhoneContact> filtered = new ArrayList<>();
                for (PhoneContact c : all) {
                    String src = resolveSource(c.accountType);
                    if (src.equals(SRC_GOOGLE)  && !inclGoogle)  continue;
                    if (src.equals(SRC_PHONE)   && !inclPhone)   continue;
                    if (src.equals(SRC_SIM)     && !inclSim)     continue;
                    if (src.equals(SRC_WA)      && !inclWa)      continue;
                    if (src.equals(SRC_WA_BIZ)  && !inclWaBiz)   continue;
                    if (src.equals(SRC_OTHER)   && !inclOther)   continue;
                    filtered.add(c);
                }

                int total = filtered.size();
                Map<String, List<PhoneContact>> phoneMap = new LinkedHashMap<>();
                for (PhoneContact c : filtered) {
                    String norm = normalizePhone(c.phone);
                    if (!norm.isEmpty()) {
                        if (!phoneMap.containsKey(norm)) phoneMap.put(norm, new ArrayList<>());
                        phoneMap.get(norm).add(c);
                    }
                }
                duplicateGroups.clear();
                int dupCount = 0;
                for (Map.Entry<String, List<PhoneContact>> e : phoneMap.entrySet()) {
                    if (e.getValue().size() > 1) {
                        duplicateGroups.add(new DuplicateGroup(e.getKey(), e.getValue()));
                        dupCount += e.getValue().size() - 1;
                    }
                }
                int uniqueCount = total - dupCount;

                // Build source map with Google account name info
                Map<String, Integer> srcMap = new LinkedHashMap<>();
                Map<String, Set<String>> googleAccounts = new LinkedHashMap<>();
                for (PhoneContact c : filtered) {
                    String src = resolveSource(c.accountType);
                    srcMap.put(src, srcMap.getOrDefault(src, 0) + 1);
                    if (src.equals(SRC_GOOGLE) && c.accountName != null && !c.accountName.isEmpty()) {
                        if (!googleAccounts.containsKey(src)) googleAccounts.put(src, new LinkedHashSet<>());
                        googleAccounts.get(src).add(c.accountName);
                    }
                }
                StringBuilder sb = new StringBuilder();
                for (Map.Entry<String, Integer> e : srcMap.entrySet()) {
                    sb.append("• ").append(e.getKey()).append(": ").append(e.getValue()).append(" kontak");
                    if (e.getKey().equals(SRC_GOOGLE) && googleAccounts.containsKey(SRC_GOOGLE)) {
                        sb.append("\n  📧 Akun: ");
                        sb.append(String.join(", ", googleAccounts.get(SRC_GOOGLE)));
                    }
                    sb.append("\n");
                }

                final int fTotal = total, fDup = dupCount, fUniq = uniqueCount;
                final String fSrc = sb.toString().trim();
                final List<PhoneContact> fFiltered = filtered;

                mainHandler.post(() -> {
                    lastFilteredContacts = fFiltered;
                    progressStats.setVisibility(View.GONE);
                    tvStatsTotal.setText("📱 Total Kontak (filter): " + fTotal);
                    tvStatsUnique.setText("✨ Unik: " + fUniq + " kontak");
                    tvStatsDuplicate.setText("🔁 Duplikat: " + fDup + " kontak");
                    tvStatsSource.setText("📂 Sumber Kontak:\n" + fSrc);
                    btnDeleteDuplicates.setEnabled(fDup > 0);
                    btnDeleteAll.setEnabled(fTotal > 0);
                    btnBackupFilterAll.setEnabled(fTotal > 0);
                    btnBackupFilterUnique.setEnabled(fTotal > 0);
                    btnViewContacts.setEnabled(fTotal > 0);
                    if (fDup == 0) tvStatsStatus.setText("✅ Tidak ada duplikat!");
                    else tvStatsStatus.setText("Siap.");
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    progressStats.setVisibility(View.GONE);
                    tvStatsStatus.setText("❌ Error: " + e.getMessage());
                });
            }
        });
    }

    // ─── LIHAT KONTAK DIALOG ─────────────────────────────────────────────────────

    private void showContactListDialog() {
        if (lastFilteredContacts.isEmpty()) {
            Toast.makeText(this, "Tidak ada kontak untuk ditampilkan.", Toast.LENGTH_SHORT).show();
            return;
        }

        // Build sets of IDs: will-be-deleted (merah) and kept-but-duplicate (orange)
        Set<Long> toDeleteIds = new HashSet<>();
        Set<Long> keptDupIds  = new HashSet<>();

        for (DuplicateGroup g : duplicateGroups) {
            List<PhoneContact> sorted = new ArrayList<>(g.contacts);
            sorted.sort((a, b) -> {
                int ia = priorityOrder.indexOf(resolveSource(a.accountType));
                int ib = priorityOrder.indexOf(resolveSource(b.accountType));
                if (ia < 0) ia = priorityOrder.size();
                if (ib < 0) ib = priorityOrder.size();
                return Integer.compare(ia, ib);
            });
            keptDupIds.add(sorted.get(0).rawContactId);
            for (int i = 1; i < sorted.size(); i++) toDeleteIds.add(sorted.get(i).rawContactId);
        }

        // Sort: merah (hapus) dulu → orange (dup dipertahankan) → hijau (unik), lalu nama
        List<PhoneContact> sorted = new ArrayList<>(lastFilteredContacts);
        sorted.sort((a, b) -> {
            int ra = toDeleteIds.contains(a.rawContactId) ? 0 : keptDupIds.contains(a.rawContactId) ? 1 : 2;
            int rb = toDeleteIds.contains(b.rawContactId) ? 0 : keptDupIds.contains(b.rawContactId) ? 1 : 2;
            if (ra != rb) return Integer.compare(ra, rb);
            String na = a.name == null ? "" : a.name;
            String nb = b.name == null ? "" : b.name;
            return na.compareToIgnoreCase(nb);
        });

        // Count for title
        int cRed = 0, cOrange = 0, cGreen = 0;
        for (PhoneContact c : sorted) {
            if (toDeleteIds.contains(c.rawContactId)) cRed++;
            else if (keptDupIds.contains(c.rawContactId)) cOrange++;
            else cGreen++;
        }

        float dp = getResources().getDisplayMetrics().density;
        int pad = (int)(12 * dp);

        // Legend header
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setBackgroundColor(0xFF0F172A);
        header.setPadding(pad, pad, pad, pad);

        LinearLayout legendRow = new LinearLayout(this);
        legendRow.setOrientation(LinearLayout.HORIZONTAL);
        addLegendItem(legendRow, "🔴 Hapus  ", 0xFFEF4444);
        addLegendItem(legendRow, "🟠 Dup dipertahankan  ", 0xFFF97316);
        addLegendItem(legendRow, "🟢 Unik", 0xFF34D399);
        header.addView(legendRow);

        TextView tvCount = new TextView(this);
        tvCount.setText("Total: " + sorted.size() + " kontak  |  🔴" + cRed + "  🟠" + cOrange + "  🟢" + cGreen);
        tvCount.setTextColor(0xFF64748B);
        tvCount.setTextSize(11);
        tvCount.setPadding(0, (int)(6*dp), 0, 0);
        header.addView(tvCount);

        // ListView with ArrayAdapter — recycles views automatically, handles 100k+ contacts
        android.widget.ListView listView = new android.widget.ListView(this);
        listView.setBackgroundColor(0xFF0F172A);
        listView.setDivider(null);
        listView.setDividerHeight((int)(4 * dp));

        final Set<Long> finalToDelete = toDeleteIds;
        final Set<Long> finalKeptDup  = keptDupIds;

        android.widget.ArrayAdapter<PhoneContact> adapter = new android.widget.ArrayAdapter<PhoneContact>(
                this, 0, sorted) {
            @Override
            public android.view.View getView(int position, android.view.View convertView, android.view.ViewGroup parent) {
                // ViewHolder pattern for smooth scrolling
                ViewHolder vh;
                if (convertView == null) {
                    LinearLayout rowOuter = new LinearLayout(getContext());
                    rowOuter.setOrientation(LinearLayout.HORIZONTAL);

                    android.view.View bar = new android.view.View(getContext());
                    LinearLayout.LayoutParams barLp = new LinearLayout.LayoutParams((int)(4*dp), LinearLayout.LayoutParams.MATCH_PARENT);
                    bar.setLayoutParams(barLp);

                    LinearLayout inner = new LinearLayout(getContext());
                    inner.setOrientation(LinearLayout.VERTICAL);
                    inner.setBackgroundColor(0xFF1E293B);
                    inner.setPadding((int)(10*dp), (int)(8*dp), (int)(10*dp), (int)(8*dp));
                    inner.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

                    TextView tvName  = new TextView(getContext());
                    tvName.setTextSize(13); tvName.setTextColor(0xFFE2E8F0);
                    tvName.setTypeface(null, android.graphics.Typeface.BOLD);

                    TextView tvPhone = new TextView(getContext());
                    tvPhone.setTextSize(11); tvPhone.setTextColor(0xFF94A3B8);

                    TextView tvSrc   = new TextView(getContext());
                    tvSrc.setTextSize(10);

                    inner.addView(tvName);
                    inner.addView(tvPhone);
                    inner.addView(tvSrc);

                    rowOuter.addView(bar);
                    rowOuter.addView(inner);
                    convertView = rowOuter;

                    vh = new ViewHolder();
                    vh.bar = bar; vh.tvName = tvName; vh.tvPhone = tvPhone; vh.tvSrc = tvSrc;
                    convertView.setTag(vh);
                } else {
                    vh = (ViewHolder) convertView.getTag();
                }

                PhoneContact c = getItem(position);
                int color;
                String label;
                if (finalToDelete.contains(c.rawContactId)) {
                    color = 0xFFEF4444; label = "🔴 HAPUS";
                } else if (finalKeptDup.contains(c.rawContactId)) {
                    color = 0xFFF97316; label = "🟠 DUPLIKAT";
                } else {
                    color = 0xFF34D399; label = "🟢 UNIK";
                }

                vh.bar.setBackgroundColor(color);
                vh.tvName.setText(c.name != null && !c.name.isEmpty() ? c.name : "(Tanpa Nama)");
                vh.tvPhone.setText("📞 " + (c.phone != null ? c.phone : "-"));

                String src = resolveSource(c.accountType);
                String srcDisplay = src;
                if (src.equals(SRC_GOOGLE) && c.accountName != null && !c.accountName.isEmpty())
                    srcDisplay = src + " (" + c.accountName + ")";
                vh.tvSrc.setText("📂 " + srcDisplay + "   " + label);
                vh.tvSrc.setTextColor(color);

                return convertView;
            }
        };

        listView.setAdapter(adapter);

        // Wrap header + listview in a LinearLayout
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.addView(header);
        root.addView(listView);

        // Make dialog fullscreen-ish
        AlertDialog dialog = new AlertDialog.Builder(this)
            .setTitle("📋 Daftar Kontak (" + sorted.size() + ")")
            .setView(root)
            .setPositiveButton("Tutup", null)
            .create();

        dialog.setOnShowListener(d -> {
            // Give ListView most of screen height
            int screenH = getResources().getDisplayMetrics().heightPixels;
            listView.setMinimumHeight((int)(screenH * 0.65f));
        });
        dialog.show();
    }

    static class ViewHolder {
        android.view.View bar;
        TextView tvName, tvPhone, tvSrc;
    }

    private void addLegendItem(LinearLayout parent, String text, int color) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(color);
        tv.setTextSize(10);
        parent.addView(tv);
    }

    private void confirmDeleteDuplicates() {
        int count = 0;
        for (DuplicateGroup g : duplicateGroups) count += g.contacts.size() - 1;
        final int c = count;

        // Build priority info
        StringBuilder pInfo = new StringBuilder("Prioritas hapus (dipertahankan → dihapus):\n");
        for (int i = 0; i < priorityOrder.size(); i++)
            pInfo.append(i + 1).append(". ").append(priorityOrder.get(i)).append("\n");

        new AlertDialog.Builder(this)
            .setTitle("⚠️ Hapus Duplikat")
            .setMessage("Akan menghapus " + c + " kontak duplikat.\n\n" + pInfo + "\n⚠️ Kontak dari Google Account akan ikut terhapus di server Google.\n\nLanjutkan?")
            .setPositiveButton("Ya, Hapus", (d, w) -> deleteDuplicates())
            .setNegativeButton("Batal", null).show();
    }

    private void deleteDuplicates() {
        btnDeleteDuplicates.setEnabled(false);
        progressStats.setVisibility(View.VISIBLE);
        tvStatsStatus.setText("⏳ Menyiapkan daftar hapus...");

        executor.execute(() -> {
            int deleted = 0;
            try {
                // Kumpulkan semua ID yang akan dihapus dulu
                List<Long> toDeleteIds = new ArrayList<>();
                for (DuplicateGroup g : duplicateGroups) {
                    List<PhoneContact> sorted = new ArrayList<>(g.contacts);
                    sorted.sort((a, b) -> {
                        int ia = priorityOrder.indexOf(resolveSource(a.accountType));
                        int ib = priorityOrder.indexOf(resolveSource(b.accountType));
                        if (ia < 0) ia = priorityOrder.size();
                        if (ib < 0) ib = priorityOrder.size();
                        return Integer.compare(ia, ib);
                    });
                    // index 0 = dipertahankan, sisanya dihapus
                    for (int i = 1; i < sorted.size(); i++)
                        toDeleteIds.add(sorted.get(i).rawContactId);
                }

                final int total = toDeleteIds.size();
                final Uri deleteUri = ContactsContract.RawContacts.CONTENT_URI.buildUpon()
                    .appendQueryParameter(ContactsContract.CALLER_IS_SYNCADAPTER, "true").build();

                // Batch per 500 — aman dari TransactionTooLargeException
                final int CHUNK = 500;
                int processed = 0;
                while (processed < toDeleteIds.size()) {
                    int end = Math.min(processed + CHUNK, toDeleteIds.size());
                    List<Long> chunk = toDeleteIds.subList(processed, end);

                    ArrayList<ContentProviderOperation> ops = new ArrayList<>();
                    for (Long id : chunk) {
                        ops.add(ContentProviderOperation.newDelete(deleteUri)
                            .withSelection(ContactsContract.RawContacts._ID + "=?",
                                new String[]{String.valueOf(id)})
                            .build());
                    }

                    try {
                        android.content.ContentProviderResult[] results =
                            getContentResolver().applyBatch(ContactsContract.AUTHORITY, ops);
                        for (android.content.ContentProviderResult r : results)
                            if (r.count != null && r.count > 0) deleted++;
                    } catch (Exception chunkEx) {
                        // Kalau satu chunk gagal, lanjut chunk berikutnya
                    }

                    processed = end;
                    final int prog = processed, tot = total, del = deleted;
                    mainHandler.post(() ->
                        tvStatsStatus.setText("⏳ Menghapus... " + prog + "/" + tot + " (" + del + " berhasil)")
                    );
                }

                final int fd = deleted;
                mainHandler.post(() -> {
                    progressStats.setVisibility(View.GONE);
                    tvStatsStatus.setText("✅ Berhasil hapus " + fd + " duplikat!");
                    duplicateGroups.clear();
                    calculateStats();
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    progressStats.setVisibility(View.GONE);
                    tvStatsStatus.setText("❌ Error: " + e.getMessage());
                    btnDeleteDuplicates.setEnabled(true);
                });
            }
        });
    }

    // ─── BACKUP PAGE ─────────────────────────────────────────────────────────────

    private void openBackupPage() {
        showPage(layoutBackup);
        tvBackupStatus.setText("");
        tvBackupInfo.setText("⏳ Menghitung kontak...");
        btnBackupAll.setEnabled(false);
        btnBackupUnique.setEnabled(false);
        progressBackup.setVisibility(View.VISIBLE);

        executor.execute(() -> {
            try {
                List<PhoneContact> all = getAllContactsWithSource();
                int total = all.size();
                Map<String, Boolean> seen = new HashMap<>();
                int unique = 0;
                for (PhoneContact c : all) {
                    String norm = normalizePhone(c.phone);
                    if (!seen.containsKey(norm)) { seen.put(norm, true); unique++; }
                }
                final int fTotal = total, fUniq = unique;
                mainHandler.post(() -> {
                    progressBackup.setVisibility(View.GONE);
                    tvBackupInfo.setText(
                        "📋 Info Kontak Saat Ini\n\n" +
                        "📱 Total semua kontak  : " + fTotal + " kontak\n" +
                        "✨ Total kontak unik   : " + fUniq + " kontak\n" +
                        "🔁 Duplikat            : " + (fTotal - fUniq) + " kontak\n\n" +
                        "File backup akan tersimpan di:\nDownloads/ContactSaver/"
                    );
                    btnBackupAll.setEnabled(true);
                    btnBackupUnique.setEnabled(true);
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    progressBackup.setVisibility(View.GONE);
                    tvBackupInfo.setText("❌ Error: " + e.getMessage());
                });
            }
        });
    }

    private void startBackup(boolean uniqueOnly) {
        btnBackupAll.setEnabled(false);
        btnBackupUnique.setEnabled(false);
        progressBackup.setVisibility(View.VISIBLE);
        tvBackupStatus.setText("⏳ Memuat kontak...");

        executor.execute(() -> {
            try {
                List<PhoneContact> all = getAllContactsWithSource();
                List<PhoneContact> toBackup;
                if (uniqueOnly) {
                    Map<String, Boolean> seen = new HashMap<>();
                    toBackup = new ArrayList<>();
                    for (PhoneContact c : all) {
                        String norm = normalizePhone(c.phone);
                        if (!seen.containsKey(norm)) { seen.put(norm, true); toBackup.add(c); }
                    }
                } else {
                    toBackup = all;
                }

                String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
                String label = uniqueOnly ? "unik" : "all";

                File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "ContactSaver");
                if (!dir.exists()) dir.mkdirs();

                File csvFile = new File(dir, "backup_" + label + "_" + timestamp + ".csv");
                File vcfFile = new File(dir, "backup_" + label + "_" + timestamp + ".vcf");

                mainHandler.post(() -> tvBackupStatus.setText("⏳ Menulis file CSV..."));
                BufferedWriter csvWriter = new BufferedWriter(new FileWriter(csvFile));
                csvWriter.write("nama,nomor,sumber\n");
                for (PhoneContact c : toBackup) {
                    String src = resolveSource(c.accountType);
                    csvWriter.write("\"" + safe(c.name) + "\",\"" + safe(c.phone) + "\",\"" + src + "\"\n");
                }
                csvWriter.close();

                mainHandler.post(() -> tvBackupStatus.setText("⏳ Menulis file VCF..."));
                BufferedWriter vcfWriter = new BufferedWriter(new FileWriter(vcfFile));
                for (PhoneContact c : toBackup) {
                    vcfWriter.write("BEGIN:VCARD\n");
                    vcfWriter.write("VERSION:3.0\n");
                    vcfWriter.write("FN:" + safe(c.name) + "\n");
                    vcfWriter.write("TEL;TYPE=CELL:" + safe(c.phone) + "\n");
                    vcfWriter.write("END:VCARD\n\n");
                }
                vcfWriter.close();

                final int count = toBackup.size();
                final String csvPath = csvFile.getAbsolutePath();
                final String vcfPath = vcfFile.getAbsolutePath();

                mainHandler.post(() -> {
                    progressBackup.setVisibility(View.GONE);
                    btnBackupAll.setEnabled(true);
                    btnBackupUnique.setEnabled(true);
                    tvBackupStatus.setText(
                        "✅ Backup selesai!\n\n" +
                        "📦 " + count + " kontak di-backup\n\n" +
                        "📄 CSV: " + csvPath + "\n\n" +
                        "📋 VCF: " + vcfPath
                    );
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    progressBackup.setVisibility(View.GONE);
                    btnBackupAll.setEnabled(true);
                    btnBackupUnique.setEnabled(true);
                    tvBackupStatus.setText("❌ Error: " + e.getMessage());
                });
            }
        });
    }

    // ─── HAPUS SEMUA ─────────────────────────────────────────────────────────────

    private void confirmDeleteAll() {
        int count = lastFilteredContacts.size();
        if (count == 0) {
            Toast.makeText(this, "Tidak ada kontak yang dipilih filter.", Toast.LENGTH_SHORT).show();
            return;
        }

        // Build sumber yang aktif
        List<String> activeFilters = new ArrayList<>();
        if (chkGoogle.isChecked())      activeFilters.add("Google Account");
        if (chkPhone.isChecked())       activeFilters.add("Memori HP");
        if (chkSim.isChecked())         activeFilters.add("SIM Card");
        if (chkWa.isChecked())          activeFilters.add("WhatsApp");
        if (chkWaBusiness.isChecked())  activeFilters.add("WA Bisnis");
        if (chkOther.isChecked())       activeFilters.add("Sumber Lain");

        String srcLabel = activeFilters.isEmpty() ? "semua sumber" : String.join(", ", activeFilters);

        new AlertDialog.Builder(this)
            .setTitle("🚨 HAPUS SEMUA Kontak")
            .setMessage("Akan menghapus SEMUA " + count + " kontak dari:\n" + srcLabel +
                "\n\n⚠️ Kontak dari Google Account akan ikut terhapus di server Google." +
                "\n\n❗ Aksi ini TIDAK BISA dibatalkan!\n\nLanjutkan?")
            .setPositiveButton("Ya, Hapus Semua", (d, w) -> deleteAllFiltered())
            .setNegativeButton("Batal", null)
            .show();
    }

    private void deleteAllFiltered() {
        if (lastFilteredContacts.isEmpty()) return;

        btnDeleteAll.setEnabled(false);
        btnDeleteDuplicates.setEnabled(false);
        progressStats.setVisibility(View.VISIBLE);
        tvStatsStatus.setText("⏳ Menyiapkan daftar hapus...");

        final List<Long> toDeleteIds = new ArrayList<>();
        for (PhoneContact c : lastFilteredContacts) toDeleteIds.add(c.rawContactId);

        executor.execute(() -> {
            int deleted = 0;
            final int total = toDeleteIds.size();
            final Uri deleteUri = ContactsContract.RawContacts.CONTENT_URI.buildUpon()
                .appendQueryParameter(ContactsContract.CALLER_IS_SYNCADAPTER, "true").build();

            // Batch 500 per chunk — optimal balance antara speed & TransactionTooLarge
            final int CHUNK = 500;
            int processed = 0;

            while (processed < toDeleteIds.size()) {
                int end = Math.min(processed + CHUNK, toDeleteIds.size());
                List<Long> chunk = toDeleteIds.subList(processed, end);

                ArrayList<ContentProviderOperation> ops = new ArrayList<>();
                for (Long id : chunk) {
                    ops.add(ContentProviderOperation.newDelete(deleteUri)
                        .withSelection(ContactsContract.RawContacts._ID + "=?",
                            new String[]{String.valueOf(id)})
                        .build());
                }
                try {
                    android.content.ContentProviderResult[] results =
                        getContentResolver().applyBatch(ContactsContract.AUTHORITY, ops);
                    for (android.content.ContentProviderResult r : results)
                        if (r.count != null && r.count > 0) deleted++;
                } catch (Exception chunkEx) {
                    // Lanjut chunk berikutnya jika ada error
                }

                processed = end;
                final int prog = processed, del = deleted;
                mainHandler.post(() ->
                    tvStatsStatus.setText("⏳ Menghapus semua... " + prog + "/" + total + " (" + del + " berhasil)")
                );
            }

            final int fd = deleted;
            mainHandler.post(() -> {
                progressStats.setVisibility(View.GONE);
                tvStatsStatus.setText("✅ Berhasil hapus " + fd + " kontak!");
                lastFilteredContacts.clear();
                duplicateGroups.clear();
                calculateStats(); // refresh stats
            });
        });
    }

    // ─── BACKUP FILTER AKTIF ─────────────────────────────────────────────────────

    private void startBackupFiltered(boolean uniqueOnly) {
        if (lastFilteredContacts.isEmpty()) {
            Toast.makeText(this, "Tidak ada kontak. Terapkan filter dulu.", Toast.LENGTH_SHORT).show();
            return;
        }

        btnBackupFilterAll.setEnabled(false);
        btnBackupFilterUnique.setEnabled(false);
        progressStats.setVisibility(View.VISIBLE);
        tvStatsStatus.setText("⏳ Menyiapkan backup...");

        executor.execute(() -> {
            try {
                List<PhoneContact> toBackup;
                if (uniqueOnly) {
                    // Ambil hanya yang nomor unik (pertama muncul)
                    Map<String, Boolean> seen = new LinkedHashMap<>();
                    toBackup = new ArrayList<>();
                    for (PhoneContact c : lastFilteredContacts) {
                        String norm = normalizePhone(c.phone);
                        if (!seen.containsKey(norm)) {
                            seen.put(norm, true);
                            toBackup.add(c);
                        }
                    }
                } else {
                    toBackup = new ArrayList<>(lastFilteredContacts);
                }

                String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
                String label = uniqueOnly ? "filter_unik" : "filter_all";

                File dir = new File(Environment.getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_DOWNLOADS), "ContactSaver");
                if (!dir.exists()) dir.mkdirs();

                File csvFile = new File(dir, "backup_" + label + "_" + timestamp + ".csv");
                File vcfFile = new File(dir, "backup_" + label + "_" + timestamp + ".vcf");

                final int total = toBackup.size();
                mainHandler.post(() -> tvStatsStatus.setText("⏳ Menulis CSV... (0/" + total + ")"));

                BufferedWriter csvWriter = new BufferedWriter(new FileWriter(csvFile));
                csvWriter.write("nama,nomor,sumber\n");
                int i = 0;
                for (PhoneContact c : toBackup) {
                    String src = resolveSource(c.accountType);
                    csvWriter.write("\"" + safe(c.name) + "\",\"" + safe(c.phone) + "\",\"" + src + "\"\n");
                    i++;
                    if (i % 500 == 0) {
                        final int prog = i;
                        mainHandler.post(() -> tvStatsStatus.setText("⏳ Menulis CSV... (" + prog + "/" + total + ")"));
                    }
                }
                csvWriter.close();

                mainHandler.post(() -> tvStatsStatus.setText("⏳ Menulis VCF... (0/" + total + ")"));

                BufferedWriter vcfWriter = new BufferedWriter(new FileWriter(vcfFile));
                i = 0;
                for (PhoneContact c : toBackup) {
                    vcfWriter.write("BEGIN:VCARD\n");
                    vcfWriter.write("VERSION:3.0\n");
                    vcfWriter.write("FN:" + safe(c.name) + "\n");
                    vcfWriter.write("TEL;TYPE=CELL:" + safe(c.phone) + "\n");
                    vcfWriter.write("END:VCARD\n\n");
                    i++;
                    if (i % 500 == 0) {
                        final int prog = i;
                        mainHandler.post(() -> tvStatsStatus.setText("⏳ Menulis VCF... (" + prog + "/" + total + ")"));
                    }
                }
                vcfWriter.close();

                final String csvPath = csvFile.getAbsolutePath();
                final String vcfPath = vcfFile.getAbsolutePath();
                final int count = toBackup.size();

                mainHandler.post(() -> {
                    progressStats.setVisibility(View.GONE);
                    btnBackupFilterAll.setEnabled(true);
                    btnBackupFilterUnique.setEnabled(true);
                    tvStatsStatus.setText(
                        "✅ Backup selesai! " + count + " kontak\n" +
                        "📄 CSV: " + csvPath + "\n" +
                        "📋 VCF: " + vcfPath
                    );
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    progressStats.setVisibility(View.GONE);
                    btnBackupFilterAll.setEnabled(true);
                    btnBackupFilterUnique.setEnabled(true);
                    tvStatsStatus.setText("❌ Error: " + e.getMessage());
                });
            }
        });
    }

    // ─── HELPERS ─────────────────────────────────────────────────────────────────

    /** Resolve accountType string → friendly source name */
    private String resolveSource(String accountType) {
        if (accountType == null) return SRC_PHONE;
        String t = accountType.toLowerCase();
        if (t.contains("google"))                   return SRC_GOOGLE;
        if (t.contains("sim"))                      return SRC_SIM;
        if (t.equals("com.whatsapp.w4b"))           return SRC_WA_BIZ;
        if (t.contains("whatsapp"))                 return SRC_WA;
        if (t.equals("vnd.sec.contact.phone") || accountType.equals(SRC_PHONE)) return SRC_PHONE;
        if (t.equals("phone"))                      return SRC_PHONE;
        return SRC_OTHER;
    }

    private Set<String> getExistingContactPhones() {
        Set<String> phones = new HashSet<>();
        Cursor c = getContentResolver().query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            new String[]{ContactsContract.CommonDataKinds.Phone.NUMBER}, null, null, null);
        if (c != null) {
            while (c.moveToNext()) { String p = c.getString(0); if (p != null) phones.add(normalizePhone(p)); }
            c.close();
        }
        return phones;
    }

    private List<PhoneContact> getAllContactsWithSource() {
        List<PhoneContact> list = new ArrayList<>();
        Cursor c = getContentResolver().query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            new String[]{
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.RAW_CONTACT_ID,
                ContactsContract.RawContacts.ACCOUNT_TYPE,
                ContactsContract.RawContacts.ACCOUNT_NAME
            }, null, null, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC");
        if (c != null) {
            while (c.moveToNext()) {
                PhoneContact p = new PhoneContact();
                p.name = c.getString(0); p.phone = c.getString(1);
                p.rawContactId = c.getLong(2); p.accountType = c.getString(3);
                p.accountName = c.getString(4);
                list.add(p);
            }
            c.close();
        }
        return list;
    }

    private String normalizePhone(String phone) {
        if (phone == null) return "";
        String s = phone.replaceAll("[^0-9+]", "");
        if (s.startsWith("08")) s = "+628" + s.substring(2);
        if (s.startsWith("628")) s = "+" + s;
        if (s.length() > 9) s = s.substring(s.length() - 9);
        return s;
    }

    private List<ContactEntry> parseVcf(Uri uri) throws Exception {
        List<ContactEntry> list = new ArrayList<>();
        BufferedReader r = new BufferedReader(new InputStreamReader(getContentResolver().openInputStream(uri), "UTF-8"));
        ContactEntry cur = null; String line;
        while ((line = r.readLine()) != null) {
            line = line.trim();
            if (line.equalsIgnoreCase("BEGIN:VCARD")) { cur = new ContactEntry(); }
            else if (line.equalsIgnoreCase("END:VCARD")) { if (cur != null && !cur.phones.isEmpty()) list.add(cur); cur = null; }
            else if (cur != null) {
                if (line.startsWith("FN:")) cur.name = line.substring(3).trim();
                else if (line.startsWith("N:") && cur.name.isEmpty()) {
                    String[] p = line.substring(2).split(";");
                    cur.name = ((p.length > 1 ? p[1] : "") + " " + p[0]).trim();
                } else if (line.startsWith("TEL") && line.contains(":")) {
                    String ph = line.substring(line.indexOf(":") + 1).trim();
                    if (!ph.isEmpty()) cur.phones.add(ph);
                }
            }
        }
        r.close(); return list;
    }

    private List<ContactEntry> parseCsv(Uri uri) throws Exception {
        List<ContactEntry> list = new ArrayList<>();
        BufferedReader r = new BufferedReader(new InputStreamReader(getContentResolver().openInputStream(uri), "UTF-8"));
        List<List<String>> allRows = new ArrayList<>();
        String line;
        while ((line = r.readLine()) != null) {
            allRows.add(parseCsvLine(line));
        }
        r.close();

        if (allRows.isEmpty()) {
            detectedFormat = "File CSV Kosong";
            return list;
        }

        List<String> firstRow = allRows.get(0);
        int numCols = 0;
        for (List<String> row : allRows) {
            if (row.size() > numCols) numCols = row.size();
        }

        // 1. Check if first row is a header
        int headerMatches = 0;
        for (String col : firstRow) {
            if (isPhoneHeader(col) || isNameHeader(col)) {
                headerMatches++;
            }
        }
        boolean hasHeader = headerMatches >= 1;

        List<Integer> phoneIndices = new ArrayList<>();
        List<Integer> nameIndices = new ArrayList<>();

        if (hasHeader) {
            for (int i = 0; i < firstRow.size(); i++) {
                String col = firstRow.get(i);
                if (isPhoneHeader(col)) {
                    phoneIndices.add(i);
                } else if (isNameHeader(col)) {
                    nameIndices.add(i);
                }
            }
        }

        // If no headers found or missing one of them, fall back/verify with auto-detect
        boolean fellBackToAutoDetect = false;
        int autoPi = -1, autoNi = -1;
        if (phoneIndices.isEmpty() || nameIndices.isEmpty()) {
            int[] phoneScores = new int[numCols];
            int[] nameScores = new int[numCols];
            int scanLimit = Math.min(allRows.size(), 15);
            for (int i = 0; i < scanLimit; i++) {
                List<String> row = allRows.get(i);
                for (int j = 0; j < row.size(); j++) {
                    String val = row.get(j);
                    if (isLikelyPhone(val)) {
                        phoneScores[j]++;
                    } else if (isLikelyName(val)) {
                        nameScores[j]++;
                    }
                }
            }

            int bestPi = -1;
            int maxPhoneScore = -1;
            for (int j = 0; j < numCols; j++) {
                if (phoneScores[j] > maxPhoneScore) {
                    maxPhoneScore = phoneScores[j];
                    bestPi = j;
                }
            }

            int bestNi = -1;
            int maxNameScore = -1;
            for (int j = 0; j < numCols; j++) {
                if (j == bestPi) continue;
                if (nameScores[j] > maxNameScore) {
                    maxNameScore = nameScores[j];
                    bestNi = j;
                }
            }

            // Fallback defaults
            if (bestPi == -1 || maxPhoneScore == 0) bestPi = 1;
            if (bestNi == -1 || maxNameScore == 0) bestNi = (bestPi == 0) ? 1 : 0;

            autoPi = bestPi;
            autoNi = bestNi;

            if (phoneIndices.isEmpty()) phoneIndices.add(bestPi);
            if (nameIndices.isEmpty()) nameIndices.add(bestNi);
            fellBackToAutoDetect = true;
        }

        // Check if it's Google Contacts
        boolean isGoogleContacts = false;
        if (hasHeader) {
            for (String col : firstRow) {
                String c = col.toLowerCase().replaceAll("[^a-z0-9]", "");
                if (c.equals("phone1value") || c.equals("firstname") || c.equals("lastname")) {
                    isGoogleContacts = true;
                    break;
                }
            }
        }

        // Set detected format info
        if (isGoogleContacts) {
            detectedFormat = "Google Contacts CSV (Multi-kolom)";
        } else if (hasHeader && !fellBackToAutoDetect) {
            StringBuilder phoneCols = new StringBuilder();
            for (int idx : phoneIndices) {
                if (phoneCols.length() > 0) phoneCols.append(", ");
                if (idx < firstRow.size()) phoneCols.append("'").append(firstRow.get(idx)).append("'");
            }
            StringBuilder nameCols = new StringBuilder();
            for (int idx : nameIndices) {
                if (nameCols.length() > 0) nameCols.append(", ");
                if (idx < firstRow.size()) nameCols.append("'").append(firstRow.get(idx)).append("'");
            }
            detectedFormat = "CSV dengan Header [Nama: " + nameCols.toString() + ", Nomor: " + phoneCols.toString() + "]";
        } else {
            int pi = phoneIndices.get(0);
            int ni = nameIndices.get(0);
            detectedFormat = (hasHeader ? "CSV dengan Header Kustom" : "CSV tanpa Header") + " - Auto-detect [Nama: Kolom " + (ni + 1) + ", Nomor: Kolom " + (pi + 1) + "]";
        }

        // Determine if we should skip the first row (header row)
        boolean skipFirstRow = hasHeader;
        if (!skipFirstRow && allRows.size() > 1) {
            int pi = phoneIndices.get(0);
            if (pi < firstRow.size()) {
                String firstVal = firstRow.get(pi);
                if (!isLikelyPhone(firstVal)) {
                    int phoneCountInSubsequent = 0;
                    int checkLimit = Math.min(allRows.size(), 10);
                    for (int i = 1; i < checkLimit; i++) {
                        List<String> row = allRows.get(i);
                        if (pi < row.size() && isLikelyPhone(row.get(pi))) {
                            phoneCountInSubsequent++;
                        }
                    }
                    if (phoneCountInSubsequent > 0) {
                        skipFirstRow = true;
                        // Adjust detectedFormat name since first row was indeed a header
                        detectedFormat = "CSV dengan Header (Unrecognized) - Auto-detect [Nama: Kolom " + (nameIndices.get(0) + 1) + ", Nomor: Kolom " + (pi + 1) + "]";
                    }
                }
            }
        }

        // 3. Parse all data rows
        int startRow = skipFirstRow ? 1 : 0;
        for (int i = startRow; i < allRows.size(); i++) {
            List<String> row = allRows.get(i);
            
            // Build name
            StringBuilder sbName = new StringBuilder();
            for (int idx : nameIndices) {
                if (idx < row.size()) {
                    String val = row.get(idx).trim();
                    if (!val.isEmpty()) {
                        if (sbName.length() > 0) sbName.append(" ");
                        sbName.append(val);
                    }
                }
            }
            
            // Get all phone numbers
            List<String> rowPhones = new ArrayList<>();
            for (int idx : phoneIndices) {
                if (idx < row.size()) {
                    String phone = row.get(idx).trim();
                    if (!phone.isEmpty()) {
                        rowPhones.add(phone);
                    }
                }
            }
            
            if (!rowPhones.isEmpty()) {
                ContactEntry entry = new ContactEntry();
                entry.name = sbName.toString().trim();
                if (entry.name.isEmpty()) {
                    entry.name = rowPhones.get(0);
                }
                entry.phones.addAll(rowPhones);
                list.add(entry);
            }
        }

        return list;
    }

    private List<String> parseCsvLine(String line) {
        List<String> result = new ArrayList<>();
        boolean inQuotes = false;
        StringBuilder curVal = new StringBuilder();
        int len = line.length();
        for (int i = 0; i < len; i++) {
            char c = line.charAt(i);
            if (c == '\"') {
                if (inQuotes && i + 1 < len && line.charAt(i + 1) == '\"') {
                    curVal.append('\"');
                    i++; // skip next quote
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (c == ',' && !inQuotes) {
                result.add(curVal.toString().trim());
                curVal.setLength(0);
            } else {
                curVal.append(c);
            }
        }
        result.add(curVal.toString().trim());
        return result;
    }

    private boolean isPhoneHeader(String s) {
        if (s == null) return false;
        String h = s.toLowerCase().replaceAll("[^a-z0-9]", "");
        if (h.contains("label") || h.contains("type")) return false;
        return h.contains("phone") || h.contains("tel") || h.contains("hp") || h.contains("nomor") || h.contains("mobile") || h.contains("contact");
    }

    private boolean isNameHeader(String s) {
        if (s == null) return false;
        String h = s.toLowerCase().replaceAll("[^a-z0-9]", "");
        if (h.contains("phonetic")) return false;
        return h.contains("name") || h.contains("nama") || h.contains("display") || h.contains("given") || h.contains("family") || h.contains("first") || h.contains("last");
    }

    private boolean isLikelyPhone(String val) {
        if (val == null) return false;
        String trimmed = val.trim();
        if (trimmed.isEmpty()) return false;
        if (!trimmed.matches("^[0-9+\\s\\-().]+$")) return false;
        int digitCount = 0;
        for (int i = 0; i < trimmed.length(); i++) {
            if (Character.isDigit(trimmed.charAt(i))) {
                digitCount++;
            }
        }
        return digitCount >= 6 && digitCount <= 16;
    }

    private boolean isLikelyName(String val) {
        if (val == null) return false;
        String trimmed = val.trim();
        if (trimmed.isEmpty()) return false;
        if (isLikelyPhone(trimmed)) return false;
        boolean hasLetter = false;
        for (int i = 0; i < trimmed.length(); i++) {
            if (Character.isLetter(trimmed.charAt(i))) {
                hasLetter = true;
                break;
            }
        }
        return hasLetter;
    }

    private int saveContactsBatch(List<ContactEntry> contacts, String accountType, String accountName) {
        // Build one giant ops list with chunks of 200 contacts per applyBatch call
        // Each contact = 1 RawContact insert + 1 StructuredName + N phone inserts
        // Using backReferences within each chunk — fastest method for bulk inserts
        int saved = 0;
        final int CHUNK = 200;
        int total = contacts.size();
        int processed = 0;

        while (processed < total) {
            int end = Math.min(processed + CHUNK, total);
            List<ContactEntry> chunk = contacts.subList(processed, end);

            ArrayList<ContentProviderOperation> ops = new ArrayList<>();
            int opIndex = 0;

            for (ContactEntry c : chunk) {
                int rawContactOpIndex = opIndex;
                ops.add(ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
                    .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, accountType)
                    .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, accountName).build());
                opIndex++;

                ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, rawContactOpIndex)
                    .withValue(ContactsContract.Data.MIMETYPE,
                        ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                    .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, c.name).build());
                opIndex++;

                for (String phone : c.phones) {
                    ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                        .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, rawContactOpIndex)
                        .withValue(ContactsContract.Data.MIMETYPE,
                            ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                        .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, phone)
                        .withValue(ContactsContract.CommonDataKinds.Phone.TYPE,
                            ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE).build());
                    opIndex++;
                }
            }

            try {
                getContentResolver().applyBatch(ContactsContract.AUTHORITY, ops);
                saved += chunk.size();
            } catch (Exception e) {
                // Fallback: simpan satu per satu jika batch gagal
                for (ContactEntry c : chunk) {
                    try {
                        ArrayList<ContentProviderOperation> single = new ArrayList<>();
                        single.add(ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
                            .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, accountType)
                            .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, accountName).build());
                        single.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                            .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                            .withValue(ContactsContract.Data.MIMETYPE,
                                ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                            .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, c.name).build());
                        for (String phone : c.phones) {
                            single.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                                .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                                .withValue(ContactsContract.Data.MIMETYPE,
                                    ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                                .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, phone)
                                .withValue(ContactsContract.CommonDataKinds.Phone.TYPE,
                                    ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE).build());
                        }
                        getContentResolver().applyBatch(ContactsContract.AUTHORITY, single);
                        saved++;
                    } catch (Exception ignored) {}
                }
            }

            processed = end;
            final int prog = processed, tot = total, sv = saved;
            mainHandler.post(() ->
                tvStatus.setText("⏳ Menyimpan kontak... " + prog + "/" + tot + " (" + sv + " berhasil)")
            );
        }
        return saved;
    }

    private int saveContacts(List<ContactEntry> contacts, String accountType, String accountName) {
        return saveContactsBatch(contacts, accountType, accountName);
    }

    private String safe(String s) { return s == null ? "" : s.replace("\"", "'"); }

    private void showImportPreviewDialog() {
        if (allParsedContacts == null || allParsedContacts.isEmpty()) {
            Toast.makeText(this, "Tidak ada data untuk di-preview.", Toast.LENGTH_SHORT).show();
            return;
        }

        List<ContactEntry> previewList = allParsedContacts;

        float dp = getResources().getDisplayMetrics().density;
        int pad = (int)(12 * dp);

        // Legend header
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setBackgroundColor(0xFF0F172A);
        header.setPadding(pad, pad, pad, pad);

        LinearLayout legendRow = new LinearLayout(this);
        legendRow.setOrientation(LinearLayout.HORIZONTAL);
        addLegendItem(legendRow, "🔴 Duplikat (Akan Skip)  ", 0xFFEF4444);
        addLegendItem(legendRow, "🟢 Baru (Akan Simpan)", 0xFF34D399);
        header.addView(legendRow);

        TextView tvCount = new TextView(this);
        tvCount.setText("Menampilkan " + previewList.size() + " kontak");
        tvCount.setTextColor(0xFF64748B);
        tvCount.setTextSize(11);
        tvCount.setPadding(0, (int)(6*dp), 0, 0);
        header.addView(tvCount);

        android.widget.ListView listView = new android.widget.ListView(this);
        listView.setBackgroundColor(0xFF0F172A);
        listView.setDivider(null);
        listView.setDividerHeight((int)(4 * dp));

        android.widget.ArrayAdapter<ContactEntry> adapter = new android.widget.ArrayAdapter<ContactEntry>(
                this, 0, previewList) {
            @Override
            public android.view.View getView(int position, android.view.View convertView, android.view.ViewGroup parent) {
                ViewHolder vh;
                if (convertView == null) {
                    LinearLayout rowOuter = new LinearLayout(getContext());
                    rowOuter.setOrientation(LinearLayout.HORIZONTAL);

                    android.view.View bar = new android.view.View(getContext());
                    LinearLayout.LayoutParams barLp = new LinearLayout.LayoutParams((int)(4*dp), LinearLayout.LayoutParams.MATCH_PARENT);
                    bar.setLayoutParams(barLp);

                    LinearLayout inner = new LinearLayout(getContext());
                    inner.setOrientation(LinearLayout.VERTICAL);
                    inner.setBackgroundColor(0xFF1E293B);
                    inner.setPadding((int)(10*dp), (int)(8*dp), (int)(10*dp), (int)(8*dp));
                    inner.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

                    TextView tvName  = new TextView(getContext());
                    tvName.setTextSize(13); tvName.setTextColor(0xFFE2E8F0);
                    tvName.setTypeface(null, android.graphics.Typeface.BOLD);

                    TextView tvPhone = new TextView(getContext());
                    tvPhone.setTextSize(11); tvPhone.setTextColor(0xFF94A3B8);

                    TextView tvSrc   = new TextView(getContext());
                    tvSrc.setTextSize(10);

                    inner.addView(tvName);
                    inner.addView(tvPhone);
                    inner.addView(tvSrc);

                    rowOuter.addView(bar);
                    rowOuter.addView(inner);
                    convertView = rowOuter;

                    vh = new ViewHolder();
                    vh.bar = bar; vh.tvName = tvName; vh.tvPhone = tvPhone; vh.tvSrc = tvSrc;
                    convertView.setTag(vh);
                } else {
                    vh = (ViewHolder) convertView.getTag();
                }

                ContactEntry c = getItem(position);
                boolean isDup = false;
                if (c != null) {
                    for (String p : c.phones) {
                        if (existingPhonesForPreview != null && existingPhonesForPreview.contains(normalizePhone(p))) {
                            isDup = true;
                            break;
                        }
                    }
                }

                int color = isDup ? 0xFFEF4444 : 0xFF34D399;
                String label = isDup ? "🔴 DUPLIKAT (Skip)" : "🟢 BARU (Simpan)";

                vh.bar.setBackgroundColor(color);
                vh.tvName.setText(c != null && c.name != null && !c.name.isEmpty() ? c.name : "(Tanpa Nama)");
                
                StringBuilder sbPhones = new StringBuilder();
                if (c != null) {
                    for (String p : c.phones) {
                        if (sbPhones.length() > 0) sbPhones.append(", ");
                        sbPhones.append(p);
                    }
                }
                vh.tvPhone.setText("📞 " + (sbPhones.length() > 0 ? sbPhones.toString() : "-"));
                vh.tvSrc.setText(label);
                vh.tvSrc.setTextColor(color);

                return convertView;
            }
        };

        listView.setAdapter(adapter);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.addView(header);
        root.addView(listView);

        AlertDialog dialog = new AlertDialog.Builder(this)
            .setTitle("👁️ Preview Import (" + previewList.size() + " Kontak)")
            .setView(root)
            .setPositiveButton("Tutup", null)
            .create();

        dialog.setOnShowListener(d -> {
            int screenH = getResources().getDisplayMetrics().heightPixels;
            listView.setMinimumHeight((int)(screenH * 0.65f));
        });
        dialog.show();
    }

    // ─── MODELS ──────────────────────────────────────────────────────────────────

    static class ContactEntry { String name = ""; List<String> phones = new ArrayList<>(); }
    static class PhoneContact { String name, phone, accountType, accountName; long rawContactId; }
    static class DuplicateGroup {
        String normalizedPhone; List<PhoneContact> contacts;
        DuplicateGroup(String p, List<PhoneContact> c) { normalizedPhone = p; contacts = c; }
    }
}