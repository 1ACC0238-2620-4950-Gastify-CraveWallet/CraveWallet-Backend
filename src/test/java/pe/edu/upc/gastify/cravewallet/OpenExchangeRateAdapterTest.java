package pe.edu.upc.gastify.cravewallet;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import pe.edu.upc.gastify.cravewallet.subscriptions.infrastructure.exchange.OpenExchangeRateAdapter;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import static org.assertj.core.api.Assertions.*;

class OpenExchangeRateAdapterTest {
    @Test void translatesProviderResponseWithoutExposingOtherRates() throws Exception {
        HttpServer server = server(200, "{\"result\":\"success\",\"base_code\":\"USD\",\"rates\":{\"PEN\":3.75},\"time_last_update_unix\":1791460800}");
        try { assertThat(adapter(server).fetchUsdToPen().rate()).isEqualByComparingTo("3.75"); }
        finally { server.stop(0); }
    }
    @Test void refusesErrorsAndMalformedRates() throws Exception {
        for (String response : new String[]{"{\"result\":\"error\"}", "{\"result\":\"success\",\"base_code\":\"USD\",\"rates\":{\"PEN\":-1},\"time_last_update_unix\":1}", "broken"}) {
            HttpServer server = server(200, response);
            try { assertThatThrownBy(() -> adapter(server).fetchUsdToPen()).isInstanceOf(RuntimeException.class); }
            finally { server.stop(0); }
        }
        HttpServer server = server(429, "{}");
        try { assertThatThrownBy(() -> adapter(server).fetchUsdToPen()).isInstanceOf(IllegalStateException.class); }
        finally { server.stop(0); }
    }
    private OpenExchangeRateAdapter adapter(HttpServer server) {
        return new OpenExchangeRateAdapter(new ObjectMapper(), "http://127.0.0.1:" + server.getAddress().getPort() + "/rates");
    }
    private HttpServer server(int status, String content) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/rates", exchange -> {
            byte[] data = content.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, data.length);
            try (var output = exchange.getResponseBody()) { output.write(data); }
        });
        server.start(); return server;
    }
}
