package com.anywherecommerce.anypaysampleapp;

import androidx.appcompat.app.AppCompatActivity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

public class MainActivity extends AppCompatActivity {
    protected Button propayButton, ppsButton, worldnetButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (!PermissionsController.verifyAppPermissions(this)) {
            PermissionsController.requestAppPermissions(this, PermissionsController.permissions, 1001);
        }


        propayButton =  findViewById(R.id.propayTerminal);
        ppsButton =  findViewById(R.id.ppsTermianl);
        worldnetButton =  findViewById(R.id.worldnetLogin);

        propayButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(MainActivity.this, PropayActivity.class);
                startActivity(intent);
            }
        });

        ppsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(MainActivity.this, PPSActivity.class);
                startActivity(intent);
            }
        });

        worldnetButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(MainActivity.this, WorldnetActivity.class);
                startActivity(intent);
            }
        });
    }
}
