package im.hoho.alipayInstallB;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

public class MainActivity extends AppCompatActivity {

    //    private Switch switchEnableMatchSee;
//    private Switch switchEnableSoul;
//
//
//    private SharedPreferences sp;
//    private SharedPreferences.Editor editor;
    private ListView listview;
    private TextView textView;

    //

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        listview = (ListView) findViewById(R.id.listview);
        textView = (TextView) findViewById(R.id.textView2);

        textView.setText("2.4.0-Gamma");

        listview.setDividerHeight(0);//屏蔽掉listview的横线
        listview.setDivider(null);

        String[] listContent = {"Author: 裘小杰 - hoho.im",
                "支付宝付款显示钻石会员背景。",
                "自定义付款码皮肤（可随机变换）.",
                "没有界面控件，请参考文档操作。",
        };
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(
                this,
                android.R.layout.simple_expandable_list_item_1,
                listContent);
        listview.setAdapter(adapter);

    }
}
