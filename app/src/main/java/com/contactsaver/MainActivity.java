package com.contactsaver;

import android.Manifest;
import android.app.AlertDialog;
import android.content.ContentProviderOperation;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.ContactsContract;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.*;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_PERMISSIONS = 100;
    private static final int PICK_FILE = 200;

    private Button btnPickFile, btnProcess, btnGoStats;
    private TextView tvFileName, tvStatus, tvResult;
    private ProgressBar progressBar;
    private LinearLayout layoutResult, layoutMain, layoutStats;

    private TextView tvStatsTotal, tvStatsDuplicate, tvStatsUnique, tvStatsSource, tvStatsStatus;
    private Button btnDeleteDuplicates, btnBackMain;
    private ProgressBar progressStats;

    private Uri selectedFileUri = null;
    private ExecutorService executor = Executors.newSingleThreadExecutor();
    private Handler mainHandler = new Handler(Looper.getMainLooper());
    private List<DuplicateGroup> duplicateGroups = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        layoutMain = findViewById(R.id.layoutMain);
        layoutStats = findViewById(R.id.layoutStats);

        btnPickFile = findViewById(R.id.btnPickFile);
        btnProcess = findViewById(R.id.btnProcess);
        btnGoStats = findViewById(R.id.btnGoStats);
        tvFileName = findViewById(R.id.tvFileName);
        tvStatus = findViewById(R.id.tvStatus);
        tvResult = findViewById(R.id.tvResult);
        progressBar = findViewById(R.id.progressBar);
        layoutResult = findViewById(R.id.layoutResult);

        tvStatsTotal = findViewById(R.id.tvStatsTotal);
        tvStatsDuplicate = findViewById(R.id.tvStatsDuplicate);
        tvStatsUnique = findViewById(R.id.tvStatsUnique);
        tvStatsSource = findViewById(R.id.tvStatsSource);
        btnDeleteDuplicates = findViewById(R.id.btnDeleteDuplicates);
        btnBackMain = findViewById(R.id.btnBackMain);
        progressStats = findViewById(R.id.progressStats);
        tvStatsStatus = findViewById(R.id.tvStatsStatus);

        btnPickFile.setOnClickListener(v -> pickFile());
        btnProcess.setOnClickListener(v -> startProcessing());
        btnGoStats.setOnClickListener(v -> openStatsPage());
        btnBackMain.setOnClickListener(v -> {
            layoutStats.setVisibility(View.GONE);
            layoutMain.setVisibility(View.VISIBLE);
        });
        btnDeleteDuplicates.setOnClickListener(v -> confirmDeleteDuplicates());

        requestPermissions();
    }

    private void requestPermissions() {
        String[] perms = {
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.WRITE_CONTACTS,
            Manifest.permission.READ_EXTERNAL_STORAGE
        };
        List<String> needed = new ArrayList<>();
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
            String name = getFileName(selectedFileUri);
            tvFileName.setText("📄 " + name);
            btnProcess.setEnabled(true);
            tvStatus.setText("File dipilih. Tap 'Proses Kontak' untuk mulai.");
            layoutResult.setVisibility(View.GONE);
        }
    }

    private String getFileName(Uri uri) {
        String result = uri.getLastPathSegment();
        if (result != null && result.contains("/"))
            result = result.substring(result.lastIndexOf("/") + 1);
        return result != null ? result : "file_kontak";
    }

    private void startProcessing() {
        if (selectedFileUri == null) {
            Toast.makeText(this, "Pilih file dulu!", Toast.LENGTH_SHORT).show();
            return;
        }
        btnProcess.setEnabled(false);
        btnPickFile.setEnabled(false);
        btnGoStats.setEnabled(false);
        progressBar.setVisibility(View.VISIBLE);
        tvStatus.setText("⏳ Membaca kontak di HP...");
        layoutResult.setVisibility(View.GONE);

        executor.execute(() -> {
            try {
                Set<String> existingPhones = getExistingContactPhones();
                mainHandler.post(() -> tvStatus.setText("⏳ Membaca file kontak..."));

                String fileName = getFileName(selectedFileUri).toLowerCase();
                List<ContactEntry> fileContacts;
                if (fileName.endsWith(".vcf") || fileName.endsWith(".vcard")) {
                    fileContacts = parseVcf(selectedFileUri);
                } else {
                    fileContacts = parseCsv(selectedFileUri);
                }

                mainHandler.post(() -> tvStatus.setText("⏳ Mengecek duplikat..."));

                List<ContactEntry> toSave = new ArrayList<>();
                int duplicates = 0;
                for (ContactEntry c : fileContacts) {
                    boolean isDuplicate = false;
                    for (String phone : c.phones) {
                        if (existingPhones.contains(normalizePhone(phone))) {
                            isDuplicate = true;
                            break;
                        }
                    }
                    if (isDuplicate) duplicates++;
                    else toSave.add(c);
                }

                final int totalFile = fileContacts.size();
                final int dupCount = duplicates;
                final int uniqueCount = toSave.size();

                mainHandler.post(() -> tvStatus.setText("⏳ Menyimpan " + uniqueCount + " kontak baru..."));
                int saved = saveContacts(toSave);

                mainHandler.post(() -> {
                    progressBar.setVisibility(View.GONE);
                    btnProcess.setEnabled(true);
                    btnPickFile.setEnabled(true);
                    btnGoStats.setEnabled(true);
                    tvStatus.setText("✅ Selesai!");
                    layoutResult.setVisibility(View.VISIBLE);
                    tvResult.setText(
                        "📊 HASIL PROSES\n\n" +
                        "📁 Total di file       : " + totalFile + " kontak\n" +
                        "🔁 Duplikat (dilewati) : " + dupCount + " kontak\n" +
                        "✨ Unik (baru)         : " + uniqueCount + " kontak\n" +
                        "💾 Berhasil disimpan   : " + saved + " kontak"
                    );
                });

            } catch (Exception e) {
                mainHandler.post(() -> {
                    progressBar.setVisibility(View.GONE);
                    btnProcess.setEnabled(true);
                    btnPickFile.setEnabled(true);
                    btnGoStats.setEnabled(true);
                    tvStatus.setText("❌ Error: " + e.getMessage());
                });
            }
        });
    }

    private void openStatsPage() {
        layoutMain.setVisibility(View.GONE);
        layoutStats.setVisibility(View.VISIBLE);
        tvStatsTotal.setText("⏳ Menghitung...");
        tvStatsDuplicate.setText("⏳");
        tvStatsUnique.setText("⏳");
        tvStatsSource.setText("⏳ Memuat sumber kontak...");
        tvStatsStatus.setText("");
        btnDeleteDuplicates.setEnabled(false);
        progressStats.setVisibility(View.VISIBLE);

        executor.execute(() -> {
            try {
                List<PhoneContact> allContacts = getAllContactsWithSource();
                int total = allContacts.size();

                Map<String, List<PhoneContact>> phoneMap = new LinkedHashMap<>();
                for (PhoneContact c : allContacts) {
                    String norm = normalizePhone(c.phone);
                    if (!norm.isEmpty()) {
                        if (!phoneMap.containsKey(norm)) phoneMap.put(norm, new ArrayList<>());
                        phoneMap.get(norm).add(c);
                    }
                }

                duplicateGroups.clear();
                int dupCount = 0;
                for (Map.Entry<String, List<PhoneContact>> entry : phoneMap.entrySet()) {
                    if (entry.getValue().size() > 1) {
                        duplicateGroups.add(new DuplicateGroup(entry.getKey(), entry.getValue()));
                        dupCount += entry.getValue().size() - 1;
                    }
                }

                int uniqueCount = total - dupCount;

                Map<String, Integer> sourceMap = new LinkedHashMap<>();
                for (PhoneContact c : allContacts) {
                    String src = c.accountType != null ? c.accountType : "Memori HP";
                    if (src.toLowerCase().contains("google")) src = "Google Account";
                    else if (src.toLowerCase().contains("sim")) src = "SIM Card";
                    else if (src.equals("vnd.sec.contact.phone") || src.equals("Memori HP")) src = "Memori HP";
                    sourceMap.put(src, sourceMap.getOrDefault(src, 0) + 1);
                }

                StringBuilder srcText = new StringBuilder();
                for (Map.Entry<String, Integer> e : sourceMap.entrySet()) {
                    srcText.append("• ").append(e.getKey()).append(": ").append(e.getValue()).append(" kontak\n");
                }

                final int fTotal = total, fDup = dupCount, fUnique = uniqueCount;
                final String fSrc = srcText.toString().trim();

                mainHandler.post(() -> {
                    progressStats.setVisibility(View.GONE);
                    tvStatsTotal.setText("📱 Total Kontak di HP: " + fTotal);
                    tvStatsDuplicate.setText("🔁 Duplikat: " + fDup + " kontak (akan dihapus)");
                    tvStatsUnique.setText("✨ Unik: " + fUnique + " kontak");
                    tvStatsSource.setText("📂 Sumber Kontak:\n" + fSrc);
                    btnDeleteDuplicates.setEnabled(fDup > 0);
                    if (fDup == 0) tvStatsStatus.setText("✅ Tidak ada duplikat ditemukan!");
                });

            } catch (Exception e) {
                mainHandler.post(() -> {
                    progressStats.setVisibility(View.GONE);
                    tvStatsStatus.setText("❌ Error: " + e.getMessage());
                });
            }
        });
    }

    private void confirmDeleteDuplicates() {
        int totalToDelete = 0;
        for (DuplicateGroup g : duplicateGroups) totalToDelete += g.contacts.size() - 1;
        final int count = totalToDelete;
        new AlertDialog.Builder(this)
            .setTitle("⚠️ Hapus Duplikat")
            .setMessage("Akan menghapus " + count + " kontak duplikat.\n\n" +
                "⚠️ Kontak dari Google Account akan ikut terhapus di server Google.\n\nLanjutkan?")
            .setPositiveButton("Ya, Hapus", (d, w) -> deleteDuplicates())
            .setNegativeButton("Batal", null)
            .show();
    }

    private void deleteDuplicates() {
        btnDeleteDuplicates.setEnabled(false);
        progressStats.setVisibility(View.VISIBLE);
        tvStatsStatus.setText("⏳ Menghapus duplikat...");

        executor.execute(() -> {
            int deleted = 0;
            try {
                for (DuplicateGroup group : duplicateGroups) {
                    for (int i = 1; i < group.contacts.size(); i++) {
                        Uri deleteUri = ContactsContract.RawContacts.CONTENT_URI.buildUpon()
                            .appendQueryParameter(ContactsContract.CALLER_IS_SYNCADAPTER, "true")
                            .build();
                        int rows = getContentResolver().delete(deleteUri,
                            ContactsContract.RawContacts._ID + "=?",
                            new String[]{String.valueOf(group.contacts.get(i).rawContactId)});
                        if (rows > 0) deleted++;
                    }
                }
                final int fDeleted = deleted;
                mainHandler.post(() -> {
                    progressStats.setVisibility(View.GONE);
                    tvStatsStatus.setText("✅ Berhasil hapus " + fDeleted + " duplikat!");
                    duplicateGroups.clear();
                    openStatsPage();
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

    private Set<String> getExistingContactPhones() {
        Set<String> phones = new HashSet<>();
        Cursor cursor = getContentResolver().query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            new String[]{ContactsContract.CommonDataKinds.Phone.NUMBER},
            null, null, null);
        if (cursor != null) {
            while (cursor.moveToNext()) {
                String p = cursor.getString(0);
                if (p != null) phones.add(normalizePhone(p));
            }
            cursor.close();
        }
        return phones;
    }

    private List<PhoneContact> getAllContactsWithSource() {
        List<PhoneContact> list = new ArrayList<>();
        Cursor cursor = getContentResolver().query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            new String[]{
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.RAW_CONTACT_ID,
                ContactsContract.RawContacts.ACCOUNT_TYPE
            },
            null, null,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC");
        if (cursor != null) {
            while (cursor.moveToNext()) {
                PhoneContact c = new PhoneContact();
                c.name = cursor.getString(0);
                c.phone = cursor.getString(1);
                c.rawContactId = cursor.getLong(2);
                c.accountType = cursor.getString(3);
                list.add(c);
            }
            cursor.close();
        }
        return list;
    }

    private String normalizePhone(String phone) {
        if (phone == null) return "";
        String cleaned = phone.replaceAll("[^0-9+]", "");
        if (cleaned.startsWith("08")) cleaned = "+628" + cleaned.substring(2);
        if (cleaned.startsWith("628")) cleaned = "+" + cleaned;
        if (cleaned.length() > 9) cleaned = cleaned.substring(cleaned.length() - 9);
        return cleaned;
    }

    private List<ContactEntry> parseVcf(Uri uri) throws Exception {
        List<ContactEntry> contacts = new ArrayList<>();
        InputStream is = getContentResolver().openInputStream(uri);
        BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
        ContactEntry current = null;
        String line;
        while ((line = reader.readLine()) != null) {
            line = line.trim();
            if (line.equalsIgnoreCase("BEGIN:VCARD")) {
                current = new ContactEntry();
            } else if (line.equalsIgnoreCase("END:VCARD")) {
                if (current != null && !current.phones.isEmpty()) contacts.add(current);
                current = null;
            } else if (current != null) {
                if (line.startsWith("FN:")) {
                    current.name = line.substring(3).trim();
                } else if (line.startsWith("N:") && current.name.isEmpty()) {
                    String[] parts = line.substring(2).split(";");
                    String fname = parts.length > 1 ? parts[1] : "";
                    String lname = parts.length > 0 ? parts[0] : "";
                    current.name = (fname + " " + lname).trim();
                } else if (line.startsWith("TEL") && line.contains(":")) {
                    String phone = line.substring(line.indexOf(":") + 1).trim();
                    if (!phone.isEmpty()) current.phones.add(phone);
                }
            }
        }
        reader.close();
        return contacts;
    }

    private List<ContactEntry> parseCsv(Uri uri) throws Exception {
        List<ContactEntry> contacts = new ArrayList<>();
        InputStream is = getContentResolver().openInputStream(uri);
        BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
        String header = reader.readLine();
        if (header == null) return contacts;
        String[] headers = header.split(",");
        int nameIdx = -1, phoneIdx = -1;
        for (int i = 0; i < headers.length; i++) {
            String h = headers[i].toLowerCase().replaceAll("[^a-z]", "");
            if (h.contains("name") || h.contains("nama")) nameIdx = i;
            if (h.contains("phone") || h.contains("tel") || h.contains("hp") || h.contains("nomor")) phoneIdx = i;
        }
        if (phoneIdx == -1) phoneIdx = 1;
        if (nameIdx == -1) nameIdx = 0;
        String line;
        while ((line = reader.readLine()) != null) {
            String[] cols = line.split(",", -1);
            if (cols.length <= phoneIdx) continue;
            String phone = cols[phoneIdx].replaceAll("\"", "").trim();
            String name = nameIdx < cols.length ? cols[nameIdx].replaceAll("\"", "").trim() : phone;
            if (!phone.isEmpty()) {
                ContactEntry e = new ContactEntry();
                e.name = name;
                e.phones.add(phone);
                contacts.add(e);
            }
        }
        reader.close();
        return contacts;
    }

    private int saveContacts(List<ContactEntry> contacts) {
        int saved = 0;
        for (ContactEntry c : contacts) {
            try {
                ArrayList<ContentProviderOperation> ops = new ArrayList<>();
                ops.add(ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
                    .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, null)
                    .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, null).build());
                ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                    .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                    .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, c.name).build());
                for (String phone : c.phones) {
                    ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                        .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                        .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                        .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, phone)
                        .withValue(ContactsContract.CommonDataKinds.Phone.TYPE, ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE).build());
                }
                getContentResolver().applyBatch(ContactsContract.AUTHORITY, ops);
                saved++;
            } catch (Exception ignored) {}
        }
        return saved;
    }

    static class ContactEntry {
        String name = "";
        List<String> phones = new ArrayList<>();
    }

    static class PhoneContact {
        String name, phone, accountType;
        long rawContactId;
    }

    static class DuplicateGroup {
        String normalizedPhone;
        List<PhoneContact> contacts;
        DuplicateGroup(String phone, List<PhoneContact> contacts) {
            this.normalizedPhone = phone;
            this.contacts = contacts;
        }
    }
}
