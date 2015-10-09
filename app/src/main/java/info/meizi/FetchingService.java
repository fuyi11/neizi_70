package info.meizi;

import android.app.IntentService;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.util.Log;

import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import info.meizi.bean.Content;
import info.meizi.net.ContentParser;
import info.meizi.net.RequestFactory;
import io.realm.Realm;

/**
 * Created by Mr_Wrong on 15/9/22.
 */
public class FetchingService extends IntentService {
    private static final String TAG = "MeiziFetchingService";
    private final OkHttpClient client = new OkHttpClient();

    public FetchingService() {
        super(TAG);
    }

    private String groupid;
    private int mcount;
    private String html;
    private List<Content> lists = new ArrayList<>();

    @Override
    protected void onHandleIntent(Intent intent) {
        groupid = intent.getStringExtra("groupid");
        Intent resuleintent = new Intent(groupid);

        Realm realm = Realm.getInstance(this);

        List<Content> latest = Content.all(realm,groupid);

        if (!latest.isEmpty()) {//数据库有  直接发送广播通知

//           return;
        } else {//否则加载网络 并存入数据库 通知
            try {
                html = client.newCall(RequestFactory.make(groupid)).execute().body().string();
            } catch (IOException e) {
                e.printStackTrace();
            }
            mcount = ContentParser.getCount(html);
            resuleintent.putExtra("count", mcount);
            sendBroadcast(resuleintent);

            for (int i = 1; i < mcount + 1; i++) {
                Content content = null;
                try {
                    content = fetchContent(groupid + "/" + i);
                    content.setOrder(Integer.parseInt(groupid + i));
                    content.setGroupid(groupid);
                } catch (IOException e) {
                    e.printStackTrace();
                }

                saveDb(realm, content);

                resuleintent.putExtra("currentcount", i);
                sendBroadcast(resuleintent);
                lists.add(content);
            }
        }
        sendBroadcast(resuleintent);
        realm.close();
    }

    private void saveDb(Realm realm, Content content) {
        realm.beginTransaction();
        realm.copyToRealm(content);
        realm.commitTransaction();
    }

    /**
     * 抓取content
     *
     * @param path
     * @return
     */
    private Content fetchContent(String path) throws IOException {
        String html;
        try {
            html = client.newCall(RequestFactory.make(path)).execute().body().string();
        } catch (IOException e) {
            Log.e(TAG, "Failed to fetch " + path, e);
            return null;
        }

        Content content = ContentParser.ParserContent(html);//这里解析获取的HTML文本

        Response response = client.newCall(new Request.Builder().url(content.getUrl()).build()).execute();
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeStream(response.body().byteStream(), null, options);
        content.setImagewidth(options.outWidth);
        content.setImageheight(options.outHeight);

        if (content == null) {
            Log.e(TAG, "cannot parse content " + path);
            return null;
        }
        return content;
    }
}
