package pe.gob.indecopi.stub;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/stub")
public class StubController {

    @GetMapping("/ping")
    public Map<String, Object> ping() {
        return Map.of("ok", true, "service", "stub-service");
    }

}
