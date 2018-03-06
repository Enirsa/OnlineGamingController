package me.gorky.OGC;

import javax.sound.sampled.*;
import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Main {

    private static final RuntimeMXBean MX_BEAN = ManagementFactory.getRuntimeMXBean();

    private static final long START_TIME_MILLISEC = MX_BEAN.getUptime();

    private static final int INTERVAL_SEC = 1;

    private static final int SLEEP_TIME_MILLISEC = INTERVAL_SEC * 1000;

    private static final int SECOND_OF_WARNING = 30 * 60;

    private static final String DB_FILENAME = "log";

    private static final String RECORD_DELIMITER = ":";

    private static final String SOUNDS_FOLDER = "sounds";

    private static final Map<String, String> SOUNDS;

    private static final Calendar BEGINNING_OF_TIME;

    // has to return JSON
    private static final String TIME_API = "http://api.timezonedb.com/v2/get-time-zone?key=ICLT981KJTBC&format=json&by=zone&zone=Europe/Kiev";

    // timestamp has to be in the first matching group
    private static final String TIMESTAMP_REGEX = ".*\"timestamp\":(\\d+).*";

    private static final SimpleDateFormat SDF = new SimpleDateFormat("dd.MM.yyyy");

    private static final Font FONT = new Font("Consolas", Font.PLAIN, 14);

    private static final Set<String> SHIT_TITLES = new HashSet<>();

    private static int uptimeSec = 0;

    private static int dailyNormSec;

    private static boolean warned = false;

    private static boolean useSecondKillingSound = false;

    private static Calendar today;

    private static int secondsLeft;

    private static int secondOfDateChange;

    private static JLabel indicator;

    static {
        SOUNDS = new HashMap<>();
        SOUNDS.put("warning", SOUNDS_FOLDER + File.separator + "warning.wav");
        SOUNDS.put("time_is_out", SOUNDS_FOLDER + File.separator + "time_is_out.wav");
        SOUNDS.put("nope", SOUNDS_FOLDER + File.separator + "nope.wav");

        BEGINNING_OF_TIME = Calendar.getInstance();
        BEGINNING_OF_TIME.setTimeInMillis(0);
    }

    public static void main(String[] args) throws Exception {
        parseArgs(args);
        today = getCurrentDay();
        secondsLeft = readSecondsLeftFromDb();
        secondOfDateChange = getSecondsTillTomorrow();
        indicator = instantiateIndicator();

        if (secondsLeft == 0) {
            useSecondKillingSound = true;
        }

        while (true) {
            try {
                doChecks();
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }

    private static void parseArgs(String[] args) throws Exception {
        if (args.length < 2) {
            throw new Exception("Invalid args");
        }

        dailyNormSec = Integer.parseInt(args[0]);
        dailyNormSec = dailyNormSec > 0 ? dailyNormSec : 0;

        for (int i = 1; i < args.length; i++) {
            SHIT_TITLES.add(args[i]);
        }
    }

    private static void doChecks() throws Exception {
        int tomorrowProximity = secondOfDateChange - (uptimeSec % 86400);

        if (tomorrowProximity <= 0 && tomorrowProximity % 10 == 0) {
            boolean wasBeginningOfTime = isSameDay(today, BEGINNING_OF_TIME);

            if (changeDayIfNeeded()) {
                boolean isBeginningOfTime = isSameDay(today, BEGINNING_OF_TIME);
                secondsLeft = isBeginningOfTime ? 0 : (wasBeginningOfTime ? readSecondsLeftFromDb() : dailyNormSec);
                secondOfDateChange = getSecondsTillTomorrow();
                useSecondKillingSound = secondsLeft == 0;
            }
        }

        String readableTime = getReadableTime(secondsLeft);

        if (!indicator.getText().equals(readableTime)) {
            indicator.setText(readableTime);
            save();
        }

        boolean shitIsRunning = WinAPI.windowIsInFocus(SHIT_TITLES);

        if (shitIsRunning) {
            if (secondsLeft > 0 && secondsLeft <= SECOND_OF_WARNING && !warned) {
                playSound("warning");
                warned = true;
            } else if (secondsLeft <= 0 && !useSecondKillingSound) {
                playSound("time_is_out");
                //WinAPI.killProcess();
                //WinAPI.closeWindow();
                WinAPI.minimizeWindow();
                useSecondKillingSound = true;
            } else if (secondsLeft <= 0) {
                playSound("nope");
                //WinAPI.killProcess();
                //WinAPI.closeWindow();
                WinAPI.minimizeWindow();
            }
        }

        int uptimeMillisec = (int) (MX_BEAN.getUptime() - START_TIME_MILLISEC);
        int incrementSec = (int) Math.ceil((double) uptimeMillisec / SLEEP_TIME_MILLISEC) - uptimeSec;
        incrementSec = incrementSec > 0 ? incrementSec : 1;
        int sleepTimeMillisec = SLEEP_TIME_MILLISEC - (uptimeMillisec % SLEEP_TIME_MILLISEC);
        uptimeSec += incrementSec;
        secondsLeft -= (secondsLeft > 0 && shitIsRunning) ? incrementSec : 0;
        TimeUnit.MILLISECONDS.sleep(sleepTimeMillisec);
    }

    private static Calendar getCurrentDay() {
        Long timestampMillis = (long) 0;

        try {
            URL url = new URL(TIME_API);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Accept", "application/json");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);

            if (connection.getResponseCode() != 200) {
                throw new RuntimeException("HTTP error (response code " + connection.getResponseCode() + ")");
            }

            BufferedReader br = new BufferedReader(new InputStreamReader((connection.getInputStream())));

            String json = "";
            String line;

            while ((line = br.readLine()) != null) {
                json += line;
            }

            connection.disconnect();

            Pattern pattern = Pattern.compile(TIMESTAMP_REGEX);
            Matcher matcher = pattern.matcher(json);
            matcher.matches();
            timestampMillis = Long.parseLong(matcher.group(1)) * 1000;
        } catch (Exception ex) {
            System.out.println("Couldn't get the online time");
            ex.printStackTrace();
        }

        Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        calendar.setTimeInMillis(timestampMillis);

        return calendar;
    }

    private static int readSecondsLeftFromDb() {
        if (isSameDay(today, BEGINNING_OF_TIME)) { // which probably means there's no internet, so you can't play online games anyway
            return 0;
        }

        try {
            File file = new File(DB_FILENAME);

            if (file.exists() && !file.isDirectory()) {
                BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(file)));
                String str = "";
                String line;

                while ((line = br.readLine()) != null) {
                    str += line;
                }

                br.close();
                String[] strArr = str.split(RECORD_DELIMITER);

                if (strArr.length == 2) {
                    Calendar calendar = Calendar.getInstance();
                    calendar.setTime(SDF.parse(strArr[0]));

                    if (isSameDay(calendar, today)) {
                        int secondsLeft = Integer.parseInt(strArr[1]);

                        return secondsLeft > dailyNormSec ? dailyNormSec : secondsLeft;
                    }
                }
            }
        } catch (Exception ex) {
            System.out.println("Error when parsing the DB file");
            ex.printStackTrace();
        }

        return dailyNormSec;
    }

    private static boolean isSameDay(Calendar calendar1, Calendar calendar2) {
        boolean sameDay = calendar1.get(Calendar.DAY_OF_MONTH) == calendar2.get(Calendar.DAY_OF_MONTH);
        boolean sameMonth = calendar1.get(Calendar.MONTH) == calendar2.get(Calendar.MONTH);
        boolean sameYear = calendar1.get(Calendar.YEAR) == calendar2.get(Calendar.YEAR);

        return sameDay && sameMonth && sameYear;
    }

    private static int getSecondsTillTomorrow() {
        if (isSameDay(today, BEGINNING_OF_TIME)) {
            return 0;
        }

        int h = today.get(Calendar.HOUR_OF_DAY);
        int m = today.get(Calendar.MINUTE);
        int s = today.get(Calendar.SECOND);

        return 24*3600 - (h*3600 + m*60 + s);
    }

    private static JLabel instantiateIndicator() {
        JFrame frame = new JFrame();
        frame.setType(javax.swing.JFrame.Type.UTILITY);
        frame.setUndecorated(true);
        frame.setBackground(new Color(0, 0, 0, 0));

        int width = frame.getFontMetrics(FONT).stringWidth(getReadableTime(dailyNormSec));
        int height = frame.getFontMetrics(FONT).getHeight();
        Rectangle workArea = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();

        frame.setSize(width, height);
        frame.setLocation(workArea.width - width - 5, workArea.height - height - 5);

        JLabel label = new JLabel(getReadableTime(secondsLeft));
        label.setFont(FONT);
        label.setForeground(Color.WHITE);

        frame.add(label);
        frame.setVisible(true);

        return label;
    }

    private static void save() throws IOException {
        FileWriter writer = new FileWriter(DB_FILENAME, false);
        writer.write(SDF.format(today.getTime()) + RECORD_DELIMITER + secondsLeft);
        writer.flush();
        writer.close();
    }

    private static boolean changeDayIfNeeded() {
        Calendar currentDay = getCurrentDay();

        if (!isSameDay(currentDay, today)) {
            today = currentDay;
            return true;
        }

        return false;
    }

    private static String getReadableTime(int seconds) {
        int h = seconds / 3600;
        int m = (seconds - 3600*h) / 60;
        int s = seconds - 3600*h - 60*m;

        return toDoubleDigits(h) + ":" + toDoubleDigits(m) + ":" + toDoubleDigits(s);
    }

    private static String toDoubleDigits(int number) {
        return (number >= 10 ? "" : "0") + number;
    }

    private static void playSound(String name) throws IOException, UnsupportedAudioFileException, LineUnavailableException {
        File soundFile = new File(SOUNDS.get(name));
        AudioInputStream ais = AudioSystem.getAudioInputStream(soundFile);
        Clip clip = AudioSystem.getClip();
        clip.open(ais);
        clip.setFramePosition(0);
        clip.start();

        clip.addLineListener(new LineListener() {
            @Override
            public void update(LineEvent event) {
                if (event.getType() == LineEvent.Type.STOP) {
                    clip.close();
                }
            }
        });
    }

}
