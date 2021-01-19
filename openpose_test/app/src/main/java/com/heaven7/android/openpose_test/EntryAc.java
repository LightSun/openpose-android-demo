package com.heaven7.android.openpose_test;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

public class EntryAc extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.ac_entry);
    }

    public void onClickNext(View view) {
        startActivity(new Intent(this, MainActivity.class));
    }
}
