package im.hoho.alipayInstallB;

import android.app.Activity;
import android.content.SharedPreferences;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.Switch;

public class MainActivity extends AppCompatActivity {

    private EditText editText;
    private EditText editText2;
    private Switch switchYuEBao;
    private Switch switchBackground;


    private SharedPreferences sp;
    private SharedPreferences.Editor editor;

    //

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);
        sp = getSharedPreferences("prefs", Activity.MODE_WORLD_READABLE);
        editor = sp.edit();

        if (sp.getString("yuebaoIncreaseAmount", "").equals("")) {
            //first time
            editor.putString("yuebaoIncreaseAmount", "20000000");
        }

        if (sp.getString("yuebaoTotalProfit", "").equals("")) {
            //first time
            editor.putString("yuebaoTotalProfit", "172823.23");
        }

        editor.apply();


        editText = (EditText) findViewById(R.id.editText);
        editText.setText(sp.getString("yuebaoIncreaseAmount", "20000000"));

        editText2 = (EditText) findViewById(R.id.editText2);
        editText2.setText(sp.getString("yuebaoTotalProfit", "172823.23"));

        switchBackground = (Switch) findViewById(R.id.switchBackground);
        if (sp.getString("enableBackground", "true").equals("true"))
            switchBackground.setChecked(true);
        switchYuEBao = (Switch) findViewById(R.id.switchYuEBao);
        if (sp.getString("enableYuEBao", "true").equals("true"))
            switchYuEBao.setChecked(true);

        switchYuEBao.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                editor.putString("enableYuEBao", Boolean.toString(isChecked).toLowerCase());
                editor.apply();
            }
        });

        switchBackground.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                editor.putString("enableBackground", Boolean.toString(isChecked).toLowerCase());
                editor.apply();
            }
        });



        editText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (s.length() != 0) {
                    editor.putString("yuebaoIncreaseAmount", s.toString());
                    editor.apply();
                }
            }
        });

        editText2.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (s.length() != 0) {
                    editor.putString("yuebaoTotalProfit", s.toString());
                    editor.apply();
                }
            }
        });
    }
}
