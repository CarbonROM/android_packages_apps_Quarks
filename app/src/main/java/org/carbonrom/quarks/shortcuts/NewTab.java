package org.carbonrom.quarks.shortcuts;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;

import org.carbonrom.quarks.MainActivity;

public class NewTab extends Activity{

   @Override
   protected void onCreate(Bundle savedInstanceState) {
       super.onCreate(savedInstanceState);
       Context context = getApplicationContext();
       MainActivity.handleShortcuts(context, "newtab");
   }
}
