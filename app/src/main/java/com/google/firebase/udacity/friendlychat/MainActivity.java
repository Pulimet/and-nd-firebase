/**
 * Copyright Google Inc. All Rights Reserved.
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.firebase.udacity.friendlychat;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.ProgressBar;

import com.firebase.ui.auth.AuthUI;
import com.firebase.ui.auth.IdpResponse;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.annotation.Nullable;

public class MainActivity extends AppCompatActivity implements View.OnClickListener{

    private static final String TAG = "MainActivity";

    public static final String ANONYMOUS = "anonymous";
    public static final int DEFAULT_MSG_LENGTH_LIMIT = 1000;
    public static final int REQUEST_CODE_SIGN_IN = 0;

    private ListView mMessageListView;
    private MessageAdapter mMessageAdapter;
    private ProgressBar mProgressBar;
    private ImageButton mPhotoPickerButton;
    private EditText mMessageEditText;
    private Button mSendButton;

    private String mUsername;
    private FirebaseFirestore db;
    private CollectionReference msgsRef;
    private FirebaseUser mUser;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mUsername = ANONYMOUS;

        initViews();

        // Enable Send button when there's text to send
        sendButtonStateControl();
        mMessageEditText.setFilters(new InputFilter[]{new InputFilter.LengthFilter(DEFAULT_MSG_LENGTH_LIMIT)});

        initListViewAndAdapter();

        // Initialize progress bar
        mProgressBar.setVisibility(ProgressBar.INVISIBLE);

        startAuthFlow();

        // ImagePickerButton shows an image picker to upload a image for a message
        mPhotoPickerButton.setOnClickListener(this);

        // Send button sends a message and clears the EditText
        mSendButton.setOnClickListener(this);
    }

    private void initViews() {
        mProgressBar = findViewById(R.id.progressBar);
        mMessageListView = findViewById(R.id.messageListView);
        mPhotoPickerButton = findViewById(R.id.photoPickerButton);
        mMessageEditText = findViewById(R.id.messageEditText);
        mSendButton = findViewById(R.id.sendButton);
    }

    private void sendButtonStateControl() {
        mMessageEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                if (charSequence.toString().trim().length() > 0) {
                    mSendButton.setEnabled(true);
                } else {
                    mSendButton.setEnabled(false);
                }
            }

            @Override
            public void afterTextChanged(Editable editable) {
            }
        });
    }

    private void initListViewAndAdapter() {
        List<FriendlyMessage> friendlyMessages = new ArrayList<>();
        mMessageAdapter = new MessageAdapter(this, R.layout.item_message, friendlyMessages);
        mMessageListView.setAdapter(mMessageAdapter);
    }

    private void startAuthFlow() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            mUser = user;
            Log.d(TAG, "User is signed in");
            setDb();
        } else {
            Log.d(TAG, "No user is signed in");
            startAuthActivity();
        }
    }

    private void setDb() {
        if (db == null) {
            db = FirebaseFirestore.getInstance();
            msgsRef = db.collection("messages");
            listenForMsgsRef();
        }
    }

    private void listenForMsgsRef() {
        msgsRef.addSnapshotListener(new EventListener<QuerySnapshot>() {
            @Override
            public void onEvent(@Nullable QuerySnapshot snapshot, @Nullable FirebaseFirestoreException e) {
                if (e != null) {
                    Log.w(TAG, "Listen failed.", e);
                    return;
                }

                if (snapshot != null) {
                    onMsgsReceived(snapshot);
                } else {
                    Log.d(TAG, "Current data: null");
                }
            }
        });
    }

    private void onMsgsReceived(QuerySnapshot snapshot) {
        Log.d(TAG, "Current data: " + snapshot.getDocuments().size());
        for (DocumentChange dc : snapshot.getDocumentChanges()) {
            switch (dc.getType()) {
                case ADDED:
                    Log.d(TAG, "New msg: " + dc.getDocument().getData());
                    FriendlyMessage friendlyMsg = dc.getDocument().toObject(FriendlyMessage.class);
                    Log.d(TAG, "New msg: " + friendlyMsg.getText());
                    mMessageAdapter.add(friendlyMsg);
                    break;
                case MODIFIED:
                    Log.d(TAG, "Modified msg: " + dc.getDocument().getData());
                    break;
                case REMOVED:
                    Log.d(TAG, "Removed msg: " + dc.getDocument().getData());
                    break;
            }
        }
    }

    private void startAuthActivity() {

        List<AuthUI.IdpConfig> providers = Collections.singletonList(
                new AuthUI.IdpConfig.GoogleBuilder().build());

        // Create and launch sign-in intent
        startActivityForResult(
                AuthUI.getInstance()
                        .createSignInIntentBuilder()
                        .setAvailableProviders(providers)
                        .build(),
                REQUEST_CODE_SIGN_IN);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_SIGN_IN) {
            IdpResponse response = IdpResponse.fromResultIntent(data);

            if (resultCode == RESULT_OK) {
                Log.d(TAG, "Successfully signed in");
                // Successfully signed in
                mUser = FirebaseAuth.getInstance().getCurrentUser();
                setDb();
                // ...
            } else {
                Log.d(TAG, "Sign in failed");
                if (response != null && response.getError() != null) {
                    Log.d(TAG, "Error code: " + response.getError().getErrorCode());
                    Log.d(TAG, "Error message: " + response.getError().getMessage());
                }
                // Sign in failed. If response is null the user canceled the
                // sign-in flow using the back button. Otherwise check
                // response.getError().getErrorCode() and handle the error.
                // ...
            }
        }
    }


    // View.OnCLickListener
    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.sendButton:
                onSendBtnClick();
                break;
            case R.id.photoPickerButton:
                onPhotoPickerBtnClick();
                break;
        }
    }

    private void onSendBtnClick() {
        // Add to DB
        String text = mMessageEditText.getText().toString();
        FriendlyMessage data = new FriendlyMessage(text, mUsername, null);
        msgsRef.add(data);

        // Clear input box
        mMessageEditText.setText("");
    }

    private void onPhotoPickerBtnClick() {
        // TODO: Fire an intent to show an image picker
    }

    //Menu
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        return super.onOptionsItemSelected(item);
    }
}
