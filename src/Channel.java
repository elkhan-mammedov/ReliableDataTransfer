import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.StringTokenizer;

/**
 * Created by elkhan on 17/06/17.
 */
public class Channel {
    private String host;
    private int port;

    public void readChannelInfo() {
        try (BufferedReader bufferedReader = new BufferedReader(new FileReader(getClass().getResource("channelInfo").getFile()))) {
            String line = bufferedReader.readLine();
            StringTokenizer stringTokenizer = new StringTokenizer(line, " ");
            host = stringTokenizer.nextToken();
            port = Integer.valueOf(stringTokenizer.nextToken());
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }
}
