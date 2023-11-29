package im.hoho.alipayInstallB;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

public class MainActivity extends Activity {

    //

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        ListView listview = (ListView) findViewById(R.id.listview);
        TextView textView = (TextView) findViewById(R.id.textView2);

        textView.setText("2.5.0");

        listview.setDividerHeight(0);//屏蔽掉listview的横线
        listview.setDivider(null);

        String[] listContent = {"Author: 裘小杰 - hoho.im 杭州亚运会版", "支付宝付款显示钻石会员背景。", "自定义付款码皮肤（可随机变换）.", "没有界面控件，请参考文档操作。", "", "文档及源代码项目地址:", "https://github.com/nov30th/AlipayHighHeadsomeRichAndroid",

        };
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_expandable_list_item_1, listContent);
        listview.setAdapter(adapter);
        listview.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                String text = (String) ((TextView) view).getText();
                if (text.contains("https")) {
                    Uri uri = Uri.parse(text);
                    Intent intent = new Intent(Intent.ACTION_VIEW);
                    intent.setData(uri);
                    startActivity(intent);
                }
            }
        });

    }
}
