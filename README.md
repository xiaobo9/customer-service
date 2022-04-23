# customer service

## 升级历史

### uk_quick_type

## 依赖的模块

### jaudiotagger

使用 `jaudiotagger` 获得音频元信息

```xml

<dependency>
    <groupId>org</groupId>
    <artifactId>jaudiotagger</artifactId>
    <version>2.0.1</version>
</dependency>
```

```java
public class Test {

    public static void mp3(File source, File target) throws IllegalArgumentException, InputFormatException, EncoderException {
        AudioAttributes audio = new AudioAttributes();
        Encoder encoder = new Encoder();


        audio.setCodec("libmp3lame");
        EncodingAttributes attrs = new EncodingAttributes();
        attrs.setFormat("mp3");
        attrs.setAudioAttributes(audio);

        encoder.encode(source, target, attrs);
    }

    public static int getMp3TrackLength(File mp3File) {
        try {
            MP3File f = (MP3File) AudioFileIO.read(mp3File);
            MP3AudioHeader audioHeader = (MP3AudioHeader) f.getAudioHeader();
            return audioHeader.getTrackLength();
        } catch (Exception e) {
            e.printStackTrace();
            return 0;
        }
    }
}
```

### disruptor

<https://github.com/LMAX-Exchange/disruptor>

Disruptor是一个高性能的异步处理框架，一个轻量级的JMS

```xml

<dependency>
    <groupId>com.lmax</groupId>
    <artifactId>disruptor</artifactId>
    <version>3.3.6</version>
</dependency>
```

### micrometer prometheus legacy

```xml

<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-registry-prometheus</artifactId>
    <version>1.1.1</version>
</dependency>
<dependency>
<groupId>io.micrometer</groupId>
<artifactId>micrometer-spring-legacy</artifactId>
<version>1.1.1</version>
</dependency>
```

### JAVE

<https://www.sauronsoftware.it/projects/jave/index.php>

The JAVE (Java Audio Video Encoder) library is Java wrapper on the ffmpeg project. Developers can take take advantage of
JAVE to transcode audio and video files from a format to another. In example you can transcode an AVI file to a MPEG
one, you can change a DivX video stream into a (youtube like) Flash FLV one, you can convert a WAV audio file to a MP3
or a Ogg Vorbis one, you can separate and transcode audio and video tracks, you can resize videos, changing their sizes
and proportions and so on. Many other formats, containers and operations are supported by JAVE.
