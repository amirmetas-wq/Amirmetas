package ir.amirsobhani.clipyar;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import androidx.work.*;
import ir.amirsobhani.clipyar.core.*;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;

public final class MainActivity extends Activity {
    private static final String TEST_VIDEO="https://www.youtube.com/watch?v=QfO4MMFXD-w";
    private static final int BLUE=Color.rgb(38,91,217),INK=Color.rgb(28,36,53),MUTED=Color.rgb(91,105,126);
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private final Handler handler=new Handler(Looper.getMainLooper());
    private LinearLayout page,results;
    private TextView status,next,translationPreview;
    private Uri pendingVideo;
    private String captionLanguage="en";
    private Config config;
    private Store store;
    private boolean active;
    private String lastWorkState="";
    private final Runnable refresh=new Runnable() {
        @Override public void run() {
            if(!active) return;
            io.execute(()-> {
                try {
                    List<WorkInfo> infos=WorkManager.getInstance(MainActivity.this).getWorkInfosForUniqueWork(Scheduler.PIPELINE).get();
                    String message="آمادهٔ اجرا",signature="";
                    for(WorkInfo info:infos) {
                        signature=info.getId()+" "+info.getState();
                        if(info.getState()==WorkInfo.State.RUNNING) message=info.getProgress().getString("status");
                        else if(info.getState()==WorkInfo.State.ENQUEUED || info.getState()==WorkInfo.State.BLOCKED) message="در انتظار اتصال یا منابع لازم برای اجرا";
                        else if(info.getState()==WorkInfo.State.FAILED) {
                            String error=info.getOutputData().getString("error"),stage=info.getOutputData().getString("stage");
                            message=error==null?"آخرین اجرا کامل نشد؛ گزارش را باز کن":"متوقف شد · "+(stage==null?"":stage)+"\n"+error;
                        }
                        else if(info.getState()==WorkInfo.State.SUCCEEDED) message=info.getOutputData().getString("translation_sample")!=null
                            ?"آزمون مترجم کامل شد؛ نمونهٔ فارسی در همین صفحه است":"آخرین اجرا پایان یافت؛ نتیجه در گالری و گزارش";
                        else if(info.getState()==WorkInfo.State.CANCELLED) message="آخرین اجرا متوقف شد";
                    }
                    final String value=message==null?"در حال پردازش…":message,token=signature;
                    runOnUiThread(()-> {
                        if(!active) return;
                        status.setText(value);
                        if(!token.equals(lastWorkState)) {lastWorkState=token;renderClips();renderTranslationProof();}
                    });
                } catch(Exception ignored) { }
            });
            handler.postDelayed(this,3000);
        }
    };
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);config=new Config(this);config.followB1M();store=Store.get(this);
        if(state!=null) {String pending=state.getString("pending_video");if(pending!=null)pendingVideo=Uri.parse(pending);captionLanguage=state.getString("caption_language","en");}
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(Color.rgb(242,244,248));
        page=new LinearLayout(this);page.setOrientation(LinearLayout.VERTICAL);page.setPadding(dp(20),dp(18),dp(20),dp(32));
        page.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);scroll.addView(page);setContentView(scroll);
        scroll.setOnApplyWindowInsetsListener((v,insets)-> {
            page.setPadding(dp(20),dp(18)+insets.getSystemWindowInsetTop(),dp(20),dp(32)+insets.getSystemWindowInsetBottom());
            return insets;
        });
        label(page,"کلیپ‌یار Rev03",32,true,INK);label(page,"REV 03  /  ترجمهٔ فارسی روی گوشی",12,true,BLUE);
        label(page,"ویدئو کامل، استوری‌های مرتب",20,true,INK);
        label(page,"تقسیم پیوسته به بخش‌های ۶۰ثانیه‌ای با قاب عمودی ۹:۱۶؛ بخش پایانی می‌تواند کوتاه‌تر باشد.",15,false,MUTED);
        LinearLayout daily=card();
        Switch toggle=new Switch(this);toggle.setText("اجرای روزانه حوالی ساعت ۱۴ ایران");toggle.setTextColor(INK);toggle.setTextSize(16);toggle.setChecked(config.enabled());daily.addView(toggle);
        toggle.setEnabled(!BuildConfig.MANUAL_TEST_ONLY);
        next=label(daily,"",14,false,MUTED);updateNext();
        toggle.setOnCheckedChangeListener((button,enabled)-> {
            config.enabled(enabled);Scheduler.reschedule(this);updateNext();if(enabled) askNotifications();
        });
        status=label(daily,"آمادهٔ اجرا",16,true,BLUE);
        label(daily,"این نسخه برای تست دستی زیرنویس و خروجی است؛ انتخاب خودکار روزانه تا تکمیل بررسی منبع و موضوع فعال نیست.",14,false,MUTED);
        button(daily,"۱. آماده‌سازی و تست ترجمهٔ فارسی",()->run(new Data.Builder().putBoolean("test_translation",true).build()));
        translationPreview=label(daily,"",14,false,INK);renderTranslationProof();
        button(daily,"تست چانگی با زیرنویس فارسی",()->run(new Data.Builder().putString("url",TEST_VIDEO).putBoolean("persian",true).build()));
        label(daily,"نمونهٔ تأییدشده: The B1M — فرودگاه چانگی سنگاپور. زیرنویس اصلی انگلیسی دریافت و با Google ML Kit روی گوشی به فارسی ترجمه می‌شود؛ سپس کل ویدئو تقسیم می‌شود. مدل ترجمه بار اول با اینترنت دانلود می‌شود.",14,false,MUTED);
        button(daily,"توقف کار جاری",()-> {WorkManager.getInstance(this).cancelUniqueWork(Scheduler.PIPELINE);toast("درخواست توقف ثبت شد");});
        label(daily,"زمان تقریبی است و به اینترنت، باتری و محدودیت پس‌زمینهٔ اندروید بستگی دارد.",13,false,MUTED);

        LinearLayout sources=card();label(sources,"انتخاب منبع",19,true,INK);
        button(sources,"انتخاب ویدئو از گوشی",()-> {
            Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("video/*").addCategory(Intent.CATEGORY_OPENABLE)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            startActivityForResult(intent,40);
        });
        button(sources,"دادن لینک یوتیوب",()->urlDialog(null));
        button(sources,"ویدئو و زیرنویس از گوشی",this::localCaptionDialog);
        label(sources,"اگر دریافت یوتیوب محدود شد، فایل ویدئو و زیرنویس انگلیسی یا فارسی SRT/VTT را از گوشی انتخاب کن. زیرنویس انگلیسی روی گوشی ترجمه می‌شود؛ فایل اصلی تغییر نمی‌کند.",14,false,MUTED);
        button(sources,"ویدئوهای تازهٔ The B1M",this::recentB1M);
        label(sources,"کانال تأییدشده برای پیگیری: The B1M. فهرست تازه‌ها به ترتیب انتشار است؛ این فهرست رتبه‌بندی بازدید ۲۴ساعته نیست.",14,false,MUTED);
        label(sources,"منبع انتخاب خودکار: جدول جهانی ChartsList بر پایهٔ بازدید کسب‌شده در ۲۴ ساعتِ آمار سایت. آمار همان روز کنترل می‌شود؛ تاریخ انتشار ویدئو شرط انتخاب نیست.",14,false,MUTED);
        button(sources,"تنظیمات انتخاب و خروجی",this::settings);

        LinearLayout output=card();label(output,"قطعه‌های آماده",19,true,INK);
        label(output,"فایل‌های من ← Downloads / clipYar / تاریخ تهران\n0001.mp4، 0002.mp4، …\nشماره‌ها بر اساس ترتیب منبع و سپس بخش ویدئو هستند. بخش آخر ممکن است کوتاه‌تر باشد.",14,false,MUTED);
        button(output,"بازکردن دانلودها",()-> {
            try {startActivity(new Intent(android.app.DownloadManager.ACTION_VIEW_DOWNLOADS));}
            catch(ActivityNotFoundException e) {toast("فایل‌های من ← حافظه داخلی ← Download ← clipYar");}
        });
        button(output,"بازکردن گالری",()-> {
            try {startActivity(new Intent(Intent.ACTION_VIEW).setType("video/*"));}
            catch(ActivityNotFoundException e) {toast("برنامهٔ گالری گوشی را باز کن و آلبوم ClipYar را ببین");}
        });
        button(output,"تازه‌سازی فهرست",this::renderClips);
        results=new LinearLayout(this);results.setOrientation(LinearLayout.VERTICAL);output.addView(results);renderClips();
        LinearLayout info=card();button(info,"گزارش اجرا و خطاها",this::logs);
        button(info,"تنظیمات باتری و اعلان برنامه",()->startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName()))));
        label(info,"فایل‌ها روی گوشی پردازش می‌شوند. برنامه استوری را خودکار منتشر نمی‌کند؛ از دکمهٔ اشتراک یا فایل‌های من استفاده کن. نام و لینک منبع کنار هر قطعه و در Download/clipYar ذخیره می‌شود. ترجمهٔ خودکار، مخصوصاً اصطلاحات مهندسی، نیازمند بازبینی است.",14,false,MUTED);
        label(info,"نسخهٔ تست کنار نسخهٔ قبلی نصب می‌شود. برای تدوین و بازنشر نمونهٔ The B1M اجازهٔ ناشر لازم است؛ درج لینک به‌تنهایی مجوز بازنشر نیست.",13,false,MUTED);
        label(info,"پیش‌نمایش Rev03 • yt-dlp، youtubedl-android، FFmpeg و Google ML Kit\nترجمهٔ ماشینی نیازمند بازبینی است. مدل زبان بار اول دانلود می‌شود؛ بعد از آن متن روی گوشی ترجمه می‌شود. برای دریافت از یوتیوب، دسترسی خود گوشی به یوتیوب لازم است.",12,false,MUTED);
        button(info,"کد منبع و مجوزها",()->open("https://github.com/amirmetas-wq/Amirmetas/tree/clipyar/rev02"));
        if(Intent.ACTION_SEND.equals(getIntent().getAction())) urlDialog(getIntent().getStringExtra(Intent.EXTRA_TEXT));
    }
    @Override protected void onResume() { super.onResume();active=true;handler.post(refresh); }
    @Override protected void onPause() { active=false;handler.removeCallbacks(refresh);super.onPause(); }
    @Override protected void onDestroy() {handler.removeCallbacksAndMessages(null);io.shutdownNow();super.onDestroy();}
    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);if(pendingVideo!=null)state.putString("pending_video",pendingVideo.toString());state.putString("caption_language",captionLanguage);
    }
    private void renderTranslationProof() {
        if(translationPreview==null) return;String proof=store.batch("translation-proof");
        translationPreview.setText(proof==null?"برای آزمون مترجم، ابتدا دکمهٔ ۱ را بزن. این آزمون به یوتیوب درخواست نمی‌فرستد.":"نمونهٔ ترجمهٔ Google ML Kit:\n"+proof);
    }
    private void updateNext() {
        String clock=String.format(Locale.ROOT,"%02d:%02d",config.hour(),config.minute());
        String text=config.enabled()?"نوبت بعدی: "+DailyTime.next(Instant.now(),config.hour(),config.minute()).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))+" تهران":"اجرای روزانه خاموش است";
        next.setText(text+"\n"+config.count()+" ویدئو در هر نوبت • ساعت "+clock+" • حداکثر مدت هر منبعِ خودکار: "+(config.maxMinutes()==0?"نامحدود":config.maxMinutes()+" دقیقه"));
    }
    private void askNotifications() {
        if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},41);
    }
    private void run(Data input) {
        askNotifications();
        io.execute(()-> {
            try {
                List<WorkInfo> infos=WorkManager.getInstance(this).getWorkInfosForUniqueWork(Scheduler.PIPELINE).get();
                for(WorkInfo info:infos) if(!info.getState().isFinished()) {runOnUiThread(()->toast("یک کار در حال اجرا یا انتظار است؛ ابتدا آن را تمام یا متوقف کن"));return;}
                Scheduler.run(this,input);
                runOnUiThread(()-> {status.setText("درخواست اجرا ثبت شد");toast("کار در پس‌زمینه ادامه پیدا می‌کند");});
            } catch(Exception e) {runOnUiThread(()->toast("ثبت کار موفق نبود: "+e.getMessage()));}
        });
    }
    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);
        if((request==40||request==42||request==43) && result==RESULT_OK && data!=null && data.getData()!=null) {
            Uri uri=data.getData();
            try {getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);}
            catch(SecurityException ignored) { }
            if(request==40) run(new Data.Builder().putString("local",uri.toString()).build());
            else if(request==42) {
                pendingVideo=uri;toast("حالا فایل زیرنویس SRT یا VTT همان ویدئو را انتخاب کن");
                startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION),43);
            } else if(pendingVideo!=null) {
                run(new Data.Builder().putString("local",pendingVideo.toString()).putString("caption",uri.toString())
                    .putString("caption_language",captionLanguage).putBoolean("persian",true).build());pendingVideo=null;
            } else toast("دوباره ویدئو و زیرنویس را انتخاب کن");
        } else if(request==42||request==43) pendingVideo=null;
    }
    private void localCaptionDialog() {
        new AlertDialog.Builder(this).setTitle("زبان فایل زیرنویس چیست؟")
            .setItems(new String[]{"انگلیسی — به فارسی ترجمه شود","فارسی — روی تصویر حک شود"},(dialog,position)-> {
                captionLanguage=position==0?"en":"fa";
                startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("video/*").addCategory(Intent.CATEGORY_OPENABLE)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION),42);
            }).setNegativeButton("بستن",null).show();
    }
    private void urlDialog(String initial) {
        LinearLayout form=new LinearLayout(this);form.setOrientation(LinearLayout.VERTICAL);form.setPadding(dp(18),dp(8),dp(18),dp(8));
        EditText input=new EditText(this);input.setHint("https://www.youtube.com/watch?v=…");input.setText(initial);input.setTextDirection(View.TEXT_DIRECTION_LTR);form.addView(input);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_URI);
        CheckBox subtitles=new CheckBox(this);subtitles.setText("زیرنویس فارسی روی تصویر");subtitles.setChecked(true);form.addView(subtitles);
        label(form,"زیرنویس اصلی انگلیسی با Google ML Kit روی گوشی ترجمه می‌شود. اگر یوتیوب دریافت را محدود کند، کار با پیام روشن متوقف می‌شود؛ می‌توانی ویدئو و زیرنویس را از گوشی انتخاب کنی.",13,false,MUTED);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("لینک ویدئوی کامل").setView(form)
            .setPositiveButton("تقسیم و ذخیره",null).setNegativeButton("بستن",null).create();
        dialog.setOnShowListener(v->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(button-> {
            try {String url=SourcePolicy.canonical(input.getText().toString());dialog.dismiss();run(new Data.Builder().putString("url",url).putBoolean("persian",subtitles.isChecked()).build());}
            catch(IllegalArgumentException e) {input.setError(e.getMessage());}
        }));dialog.show();
    }
    private void recentB1M() {
        toast("دریافت فهرست ویدئوهای تازهٔ The B1M…");
        io.execute(()-> {
            try {
                NativeTools tools=new NativeTools(this);tools.init();
                YoutubeSource source=new YoutubeSource(this,new YoutubeSource.Progress() {
                    @Override public void update(String text) { }
                    @Override public boolean stopped() {return Thread.currentThread().isInterrupted();}
                },store);
                List<VideoCandidate> videos=source.recentB1M();String[] titles=new String[videos.size()];
                for(int i=0;i<titles.length;i++) titles[i]=videos.get(i).title;
                runOnUiThread(()-> {if(!isFinishing()) new AlertDialog.Builder(this).setTitle("تازه‌های The B1M · ترتیب انتشار")
                    .setItems(titles,(dialog,position)->urlDialog(videos.get(position).url)).setNegativeButton("بستن",null).show();});
            } catch(Exception e) {store.log("The B1M: "+YoutubeSource.shortError(e));runOnUiThread(()->toast("دریافت فهرست موفق نبود؛ گزارش اجرا را ببین"));}
        });
    }
    private void settings() {
        ScrollView scroll=new ScrollView(this);LinearLayout form=new LinearLayout(this);form.setOrientation(LinearLayout.VERTICAL);form.setPadding(dp(20),dp(8),dp(20),dp(16));form.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);scroll.addView(form);
        EditText count=number(form,"تعداد ویدئوی روزانه (۱ تا ۱۰)",config.count());
        EditText hour=number(form,"ساعت تهران (۰ تا ۲۳)",config.hour());
        EditText minute=number(form,"دقیقه (۰ تا ۵۹)",config.minute());
        EditText max=number(form,"حداکثر دقیقهٔ هر منبعِ خودکار؛ صفر یعنی نامحدود",config.maxMinutes());
        label(form,"ویدئوی طولانی‌تر در جست‌وجوی خودکار کنار گذاشته می‌شود؛ هیچ ویدئوی انتخاب‌شده‌ای ناقص بریده نمی‌شود. برای لینک دستی و فایل گوشی این سقف اعمال نمی‌شود.",13,false,MUTED);
        CheckBox hd=new CheckBox(this);hd.setText("خروجی ۱۰۸۰×۱۹۲۰ (خاموش: ۷۲۰×۱۲۸۰)");hd.setChecked(config.width()==1080);form.addView(hd);
        CheckBox wifi=new CheckBox(this);wifi.setText("دریافت فقط با Wi-Fi");wifi.setChecked(config.wifiOnly());form.addView(wifi);
        label(form,"اگر خاموش باشد، دریافت روزانه می‌تواند از اینترنت سیم‌کارت استفاده کند و ویدئوهای کامل حجم زیادی مصرف می‌کنند.",13,false,MUTED);
        CheckBox cc=new CheckBox(this);cc.setText("در جست‌وجوی خودکار فقط Creative Commons");cc.setChecked(config.ccOnly());form.addView(cc);
        label(form,"منبع: ChartsList · رتبه‌بندی بر پایهٔ ستون 24h views. رتبه، بازدید روزانه، زمان آمار و لینک منبع برای هر انتخاب قابل مشاهده است.",14,false,MUTED);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("تنظیمات کلیپ‌یار").setView(scroll).setPositiveButton("ذخیره",null).setNegativeButton("بستن",null).create();
        dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v-> {
            try {
                int n=Integer.parseInt(count.getText().toString()),h=Integer.parseInt(hour.getText().toString()),m=Integer.parseInt(minute.getText().toString()),maxValue=Integer.parseInt(max.getText().toString());
                if(n<1||n>10||h<0||h>23||m<0||m>59||maxValue<0||maxValue>10_080) throw new IllegalArgumentException();
                config.save(n,h,m,hd.isChecked()?1080:720,maxValue,wifi.isChecked(),cc.isChecked());
                Scheduler.reschedule(this);updateNext();dialog.dismiss();toast("تنظیمات ذخیره شد");
            } catch(Exception e) {toast("مقادیر تنظیمات را در محدودهٔ نوشته‌شده وارد کن");}
        }));dialog.show();
    }
    private void renderClips() {
        if(results==null) return;results.removeAllViews();
        List<Store.Clip> clips=store.clips(80);
        if(clips.isEmpty()) {label(results,"هنوز قطعه‌ای ذخیره نشده است. برای آزمایش، یک ویدئو از گوشی انتخاب کن.",14,false,MUTED);return;}
        clips.sort(Comparator.comparing((Store.Clip clip)->clip.day).reversed().thenComparingInt(clip->clip.seq));
        for(Store.Clip clip:clips) {
            label(results,clip.name()+" · "+clip.day+"\n"+clip.title,15,true,INK);
            String evidence=store.evidence(clip.day,clip.source);
            if(!evidence.isEmpty()) label(results,evidence,12,false,MUTED);
            label(results,"از "+ClipPlan.seconds(clip.start)+" تا "+ClipPlan.seconds(clip.start+clip.duration)+" ثانیه",12,false,MUTED);
            LinearLayout row=new LinearLayout(this);results.addView(row);
            button(row,"پخش",()-> {
                try {startActivity(new Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(clip.uri),"video/mp4").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION));}
                catch(Exception e) {toast("فایل در دسترس نیست یا پخش‌کننده‌ای نصب نشده");}
            });
            button(row,"اشتراک",()-> {
                try {Intent send=new Intent(Intent.ACTION_SEND).setType("video/mp4").putExtra(Intent.EXTRA_STREAM,Uri.parse(clip.uri))
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    send.setClipData(ClipData.newRawUri("video",Uri.parse(clip.uri)));
                    startActivity(Intent.createChooser(send,"اشتراک در استوری"));}
                catch(Exception e) {toast("اشتراک این فایل ممکن نشد");}
            });
            button(row,"منبع",()-> {
                String source=clip.title+"\n"+clip.channel+"\n"+clip.url+"\n"+store.evidence(clip.day,clip.source);
                getSystemService(ClipboardManager.class).setPrimaryClip(ClipData.newPlainText("منبع ویدئو",source));toast("نام و لینک منبع کپی شد");
            });
        }
        label(results,"نمایش حداکثر ۸۰ قطعهٔ اخیر؛ فایل‌های بیشتر در گالری هستند.",12,false,MUTED);
    }
    private void logs() {
        ScrollView scroll=new ScrollView(this);TextView text=new TextView(this);text.setText(store.logs());text.setTextIsSelectable(true);text.setTextColor(INK);text.setPadding(dp(18),dp(12),dp(18),dp(12));text.setTextSize(14);scroll.addView(text);
        new AlertDialog.Builder(this).setTitle("گزارش اجرا · ساعت ایران").setView(scroll).setPositiveButton("بستن",null).show();
    }
    private LinearLayout card() {
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(16),dp(14),dp(16),dp(14));
        GradientDrawable bg=new GradientDrawable();bg.setColor(Color.WHITE);bg.setCornerRadius(dp(22));box.setBackground(bg);
        LinearLayout.LayoutParams layout=new LinearLayout.LayoutParams(-1,-2);layout.topMargin=dp(16);page.addView(box,layout);return box;
    }
    private TextView label(LinearLayout parent,String text,int size,boolean bold,int color) {
        TextView view=new TextView(this);view.setText(text);view.setTextSize(size);view.setTextColor(color);view.setPadding(0,dp(5),0,dp(7));view.setLineSpacing(dp(3),1f);
        if(bold) view.setTypeface(Typeface.DEFAULT,Typeface.BOLD);parent.addView(view);return view;
    }
    private void button(LinearLayout parent,String text,Runnable action) {
        Button b=new Button(this);b.setText(text);b.setTextSize(14);b.setTextColor(BLUE);b.setAllCaps(false);b.setOnClickListener(v->action.run());
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(parent.getOrientation()==LinearLayout.HORIZONTAL?0:-1,-2);
        if(parent.getOrientation()==LinearLayout.HORIZONTAL) p.weight=1;parent.addView(b,p);
    }
    private EditText number(LinearLayout parent,String title,int value) {
        label(parent,title,14,true,INK);EditText input=new EditText(this);input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);input.setText(Integer.toString(value));parent.addView(input);return input;
    }
    private void open(String url) {try {startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url)));} catch(Exception e) {toast("مرورگر در دسترس نیست");}}
    private void toast(String message) {Toast.makeText(this,message,Toast.LENGTH_LONG).show();}
    private int dp(int value) {return Math.round(value*getResources().getDisplayMetrics().density);}
}
