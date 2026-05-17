package com.contactsaver;

import android.Manifest;
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

    private Button btnPickFile, btnProcess;
    private TextView tvFileName, tvStatus, tvResult;
    private ProgressBar progressBar;
    private LinearLayout layoutResult;

    private Uri selectedFileUri = null;
    private ExecutorService executor = Executors.newSingleThreadExecutor();
    private Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        btnPickFile = findViewById(R.id.btnPickFile);
        btnProcess = findViewById(R.id.btnProcess);
        tvFileName = findViewById(R.id.tvFileName);
        tvStatus = findViewById(R.id.tvStatus);
        tvResult = findViewById(R.id.tvResult);
        progressBar = findViewById(R.id.progressBar);
        layoutResult = findViewById(R.id.layoutResult);

        btnPickFile.setOnClickListener(v -> pickFile());
        btnProcess.setOnClickListener(v -> startProcessing());

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
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"text/vcard", "text/x-vcard", "text/csv", "text/plain"});
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
        progressBar.setVisibility(View.VISIBLE);
        tvStatus.setText("⏳ Membaca kontak di HP...");
        layoutResult.setVisibility(View.GONE);

        executor.execute(() -> {
            try {
                // Step 1: Get existing contacts from phone
                Set<String> existingPhones = getExistingContacts();

                mainHandler.post(() -> tvStatus.setText("⏳ Membaca file kontak..."));

                // Step 2: Parse file
                String fileName = getFileName(selectedFileUri).toLowerCase();
                List<ContactEntry> fileContacts;
                if (fileName.endsWith(".vcf") || fileName.endsWith(".vcard")) {
                    fileContacts = parseVcf(selectedFileUri);
                } else {
                    fileContacts = parseCsv(selectedFileUri);
                }

                mainHandler.post(() -> tvStatus.setText("⏳ Mengecek duplikat..."));

                // Step 3: Filter duplicates
                List<ContactEntry> toSave = new ArrayList<>();
                int duplicates = 0;
                for (ContactEntry c : fileContacts) {
                    boolean isDuplicate = false;
                    for (String phone : c.phones) {
                        String normalized = normalizePhone(phone);
                        if (existingPhones.contains(normalized)) {
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

                // Step 4: Save unique contacts
                int saved = saveContacts(toSave);

                mainHandler.post(() -> {
                    progressBar.setVisibility(View.GONE);
                    btnProcess.setEnabled(true);
                    btnPickFile.setEnabled(true);
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
                    tvStatus.setText("❌ Error: " + e.getMessage());
                });
            }
        });
    }

    private Set<String> getExistingContacts() {
        Set<String> phones = new HashSet<>();
        ContentResolver cr = getContentResolver();
        Cursor cursor = cr.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            new String[]{ContactsContract.CommonDataKinds.Phone.NUMBER},
            null, null, null
        );
        if (cursor != null) {
            while (cursor.moveToNext()) {
                String phone = cursor.getString(0);
                if (phone != null) phones.add(normalizePhone(phone));
            }
            cursor.close();
        }
        return phones;
    }

    private String normalizePhone(String phone) {
        if (phone == null) return "";
        String cleaned = phone.replaceAll("[^0-9+]", "");
        // Normalize Indonesian numbers
        if (cleaned.startsWith("08")) cleaned = "+628" + cleaned.substring(2);
        if (cleaned.startsWith("628")) cleaned = "+" + cleaned;
        // Remove trailing zeros issue: keep last 9 digits for comparison
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
        ContentResolver cr = getContentResolver();
        for (ContactEntry c : contacts) {
            try {
                ArrayList<ContentProviderOperation> ops = new ArrayList<>();
                ops.add(ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
                    .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, null)
                    .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, null)
                    .build());
                ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                    .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                    .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, c.name)
                    .build());
                for (String phone : c.phones) {
                    ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                        .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                        .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                        .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, phone)
                        .withValue(ContactsContract.CommonDataKinds.Phone.TYPE, ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE)
                        .build());
                }
                cr.applyBatch(ContactsContract.AUTHORITY, ops);
                saved++;
            } catch (Exception ignored) {}
        }
        return saved;
    }

    static class ContactEntry {
        String name = "";
        List<String> phones = new ArrayList<>();
    }
}
