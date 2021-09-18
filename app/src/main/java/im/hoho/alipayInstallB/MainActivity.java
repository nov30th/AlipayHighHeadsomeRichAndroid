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

        textView.setText("2.3.0 LTW");

        listview.setDividerHeight(0);//屏蔽掉listview的横线
        listview.setDivider(null);

        String[] listContent = {"Author: 裘小杰 - hoho.im",
                "支付宝付款显示钻石会员背景。",
                "适配支付宝10.2.33后(20210917)，由于共享参数失效导致大众会员问题。",
                "目前没有会员样式选择，只有钻石付款页面。"
        };
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(
                this,
                android.R.layout.simple_expandable_list_item_1,
                listContent);
        listview.setAdapter(adapter);

    }
}
