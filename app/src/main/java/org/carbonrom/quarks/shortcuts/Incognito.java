package org.carbonrom.quarks.shortcuts;

import android.os.Bundle;

import org.carbonrom.quarks.MainActivity;
import org.carbonrom.quarks.R;

public class Incognito extends MainActivity{

    @Override
    protected void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        IncognitoShortcut();
    }
}
