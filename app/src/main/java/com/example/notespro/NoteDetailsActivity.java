package com.example.notespro;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentReference;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class NoteDetailsActivity extends AppCompatActivity {

    EditText titleEditText, contentEditText;
    ImageButton saveNoteBtn;
    TextView pageTitleTextView, deleteNoteTextViewBtn;
    ImageView imageNote;
    String title, content, docId;
    boolean isEditMode = false;
    private String selectedImagePath = "";
    private static final int REQUEST_CODE_STORAGE_PERMISSION = 1;
    private static final int REQUEST_CODE_SELECT_IMAGE = 2;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_note_details);

        titleEditText = findViewById(R.id.notes_title_text);
        contentEditText = findViewById(R.id.notes_content_text);
        saveNoteBtn = findViewById(R.id.save_note_btn);
        pageTitleTextView = findViewById(R.id.page_title);
        deleteNoteTextViewBtn = findViewById(R.id.delete_note_text_view_btn);
        imageNote = findViewById(R.id.imageNote);

        // Receive data
        title = getIntent().getStringExtra("title");
        content = getIntent().getStringExtra("content");
        docId = getIntent().getStringExtra("docId");

        if (docId != null && !docId.isEmpty()) {
            isEditMode = true;
        }

        titleEditText.setText(title);
        contentEditText.setText(content);
        if (isEditMode) {
            pageTitleTextView.setText("Edit your note");
            deleteNoteTextViewBtn.setVisibility(View.VISIBLE);
        }

        saveNoteBtn.setOnClickListener(v -> saveNote());
        deleteNoteTextViewBtn.setOnClickListener(v -> deleteNoteFromFirebase());

        // Handle "Add Image" button click
        findViewById(R.id.layoutAddImage).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    // For Android 13+
                    if (ContextCompat.checkSelfPermission(
                            getApplicationContext(),
                            Manifest.permission.READ_MEDIA_IMAGES
                    ) != PackageManager.PERMISSION_GRANTED) {
                        ActivityCompat.requestPermissions(
                                NoteDetailsActivity.this,
                                new String[]{Manifest.permission.READ_MEDIA_IMAGES},
                                REQUEST_CODE_STORAGE_PERMISSION
                        );
                    } else {
                        selectImage();
                    }
                } else {
                    // For Android 12 and below
                    if (ContextCompat.checkSelfPermission(
                            getApplicationContext(),
                            Manifest.permission.READ_EXTERNAL_STORAGE
                    ) != PackageManager.PERMISSION_GRANTED) {
                        ActivityCompat.requestPermissions(
                                NoteDetailsActivity.this,
                                new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                                REQUEST_CODE_STORAGE_PERMISSION
                        );
                    } else {
                        selectImage();
                    }
                }
            }
        });
    }

    private void selectImage() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        if (intent.resolveActivity(getPackageManager()) != null) {
            startActivityForResult(intent, REQUEST_CODE_SELECT_IMAGE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_STORAGE_PERMISSION && grantResults.length > 0) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                selectImage();
            } else {
                Toast.makeText(this, "Permission Denied", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_SELECT_IMAGE && resultCode == RESULT_OK) {
            if (data != null) {
                Uri selectedImageUri = data.getData();
                if (selectedImageUri != null) {
                    try {
                        InputStream inputStream = getContentResolver().openInputStream(selectedImageUri);
                        Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
                        imageNote.setImageBitmap(bitmap);
                        imageNote.setVisibility(View.VISIBLE);
                        selectedImagePath = getPathFromUri(selectedImageUri);
                    } catch (Exception exception) {
                        Toast.makeText(this, exception.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                }
            }
        }
    }

    private String getPathFromUri(Uri uri) {
        String filePath = "";
        if (uri.getScheme().equals("content")) {
            Cursor cursor = getContentResolver().query(uri, null, null, null, null);
            if (cursor != null) {
                cursor.moveToFirst();
                int index = cursor.getColumnIndex(MediaStore.Images.Media.DATA);
                if (index != -1) {
                    filePath = cursor.getString(index);
                } else {
                    filePath = getFileFromInputStreamUri(uri);
                }
                cursor.close();
            }
        } else if (uri.getScheme().equals("file")) {
            filePath = uri.getPath();
        }
        return filePath;
    }

    private String getFileFromInputStreamUri(Uri uri) {
        String filePath = "";
        try {
            InputStream inputStream = getContentResolver().openInputStream(uri);
            if (inputStream != null) {
                File tempFile = File.createTempFile("temp_image", ".jpg", getCacheDir());
                OutputStream outputStream = new FileOutputStream(tempFile);
                byte[] buffer = new byte[4 * 1024];
                int read;
                while ((read = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, read);
                }
                outputStream.flush();
                outputStream.close();
                inputStream.close();
                filePath = tempFile.getAbsolutePath();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return filePath;
    }

    void saveNote() {
        String noteTitle = titleEditText.getText().toString();
        String noteContent = contentEditText.getText().toString();
        if (noteTitle.isEmpty()) {
            titleEditText.setError("Title is required");
            return;
        }
        Note note = new Note();
        note.setTitle(noteTitle);
        note.setContent(noteContent);
        note.setTimestamp(Timestamp.now());
        note.setImagePath(selectedImagePath);
        saveNoteToFirebase(note);
    }

    void saveNoteToFirebase(Note note) {
        DocumentReference documentReference;
        if (isEditMode) {
            documentReference = Utility.getCollectionReferenceForNotes().document(docId);
        } else {
            documentReference = Utility.getCollectionReferenceForNotes().document();
        }
        documentReference.set(note).addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                Utility.showToast(NoteDetailsActivity.this, "Note added successfully");
                finish();
            } else {
                Utility.showToast(NoteDetailsActivity.this, "Failed while adding note");
            }
        });
    }

    void deleteNoteFromFirebase() {
        DocumentReference documentReference = Utility.getCollectionReferenceForNotes().document(docId);
        documentReference.delete().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                Utility.showToast(NoteDetailsActivity.this, "Note deleted successfully");
                finish();
            } else {
                Utility.showToast(NoteDetailsActivity.this, "Failed while deleting note");
            }
        });
    }
}
