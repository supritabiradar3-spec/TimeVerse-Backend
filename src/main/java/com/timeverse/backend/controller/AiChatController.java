package com.timeverse.backend.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import com.timeverse.backend.dto.ApiResponse;
import com.timeverse.backend.service.ProductService;
import com.timeverse.backend.dto.ProductDto;
import com.timeverse.backend.repository.CategoryRepository;

import java.util.*;

@RestController
@RequestMapping("/api/ai")
@CrossOrigin(origins = "*")
public class AiChatController {

    private final ProductService productService;
    private final CategoryRepository categoryRepository;

    public AiChatController(ProductService productService, CategoryRepository categoryRepository) {
        this.productService = productService;
        this.categoryRepository = categoryRepository;
    }

    private static class ChatState {
        String activeCategory;
        Double maxPrice;
        List<Map<String, String>> history = new ArrayList<>();
    }

    private final Map<String, ChatState> chatStates = new java.util.concurrent.ConcurrentHashMap<>();

    @Value("${huggingface.api.token:}")
    private String apiToken;

    @Value("${huggingface.api.model:facebook/blenderbot-400M-distill}")
    private String modelName;

    @Value("${huggingface.api.url:https://api-inference.huggingface.co/models/}")
    private String apiUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    @PostMapping("/chat")
    public ResponseEntity<ApiResponse<Map<String, String>>> chat(
            @RequestBody Map<String, String> request,
            jakarta.servlet.http.HttpServletRequest httpServletRequest) {
        String userMessage = request.get("message");
        if (userMessage == null || userMessage.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Message cannot be empty"));
        }

        // Establish session Key using Remote Address + User Agent
        String sessionKey = httpServletRequest.getRemoteAddr();
        String userAgent = httpServletRequest.getHeader("User-Agent");
        if (userAgent != null) {
            sessionKey += "_" + userAgent;
        }
        org.springframework.security.core.Authentication auth = 
            org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
            sessionKey = auth.getName();
        }

        // Get/Create session chat state
        ChatState state = chatStates.computeIfAbsent(sessionKey, k -> new ChatState());

        // Parse user query for category updates
        String msgLower = userMessage.toLowerCase();
        if (msgLower.contains("sport")) {
            state.activeCategory = "Sports";
        } else if (msgLower.contains("luxury")) {
            state.activeCategory = "Luxury";
        } else if (msgLower.contains("analog")) {
            state.activeCategory = "Analog";
        } else if (msgLower.contains("digital")) {
            state.activeCategory = "Digital";
        }

        // Parse user query for price limits
        java.util.regex.Matcher priceMatcher = java.util.regex.Pattern.compile("(?:under|below|less than)\\s*(?:rs\\.?|inr|₹)?\\s*(\\d+)", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(userMessage);
        if (priceMatcher.find()) {
            try {
                state.maxPrice = Double.parseDouble(priceMatcher.group(1));
            } catch (Exception ignored) {}
        } else if (msgLower.contains("cheaper")) {
            if (state.maxPrice != null) {
                state.maxPrice = state.maxPrice * 0.75;
            } else {
                state.maxPrice = 20000.0;
            }
        }

        try {
            String targetUrl = apiUrl;
            Map<String, Object> body = new HashMap<>();

            if (apiUrl != null && apiUrl.contains("/v1/chat/completions")) {
                body.put("model", modelName);
                List<Map<String, String>> messages = new ArrayList<>();

                // Build category mapping
                Map<Long, String> categoryMap = new HashMap<>();
                categoryRepository.findAll().forEach(c -> categoryMap.put(c.getCategoryId(), c.getCategoryName()));

                // Retrieve available products from existing TimeVerse product data
                List<ProductDto> relevantProducts = getRelevantProducts(userMessage, state, categoryMap);

                // Build a system prompt with the product catalog context
                StringBuilder systemContent = new StringBuilder();
                systemContent.append("You are the TimeVerse AI assistant, a helpful shop assistant for TimeVerse watch store.\n");
                
                if (state.activeCategory != null) {
                    systemContent.append(String.format("The user has selected the '%s' category.\n", state.activeCategory));
                }
                
                if (!relevantProducts.isEmpty()) {
                    systemContent.append("Here is the relevant real watch catalog context from our database:\n");
                    for (ProductDto p : relevantProducts) {
                        double priceVal = p.getPrice() != null ? p.getPrice().doubleValue() : 0.0;
                        String descVal = p.getDescription() != null ? p.getDescription() : "";
                        systemContent.append(String.format("- **%s** | Price: ₹%.2f | Description: %s\n",
                                p.getName(), priceVal, descVal));
                    }
                    systemContent.append("\nRequirements:\n");
                    systemContent.append("1. Answer the customer's query using ONLY the real products listed above.\n");
                    systemContent.append("2. Include actual watch names, prices, and descriptions in a concise, natural, conversational format.\n");
                    systemContent.append("3. Do NOT make up, invent, or hallucinate any other watches or prices.\n");
                    
                    if (state.activeCategory == null) {
                        systemContent.append("4. At the end of your response, ask a simple follow-up such as: 'Would you like to see luxury, sports, analog, or digital watches?'\n");
                    } else {
                        systemContent.append("4. Since the user is already browsing the '" + state.activeCategory + "' category, do NOT ask them to choose a category. Instead, ask a follow-up related to these specific watches (e.g., if they want to know about features, see cheaper options, or check out).\n");
                    }
                }

                Map<String, String> systemMsgMap = new HashMap<>();
                systemMsgMap.put("role", "system");
                systemMsgMap.put("content", systemContent.toString());
                messages.add(systemMsgMap);

                // Add conversation history context
                messages.addAll(state.history);

                // Add current user message
                Map<String, String> userMsgMap = new HashMap<>();
                userMsgMap.put("role", "user");
                userMsgMap.put("content", userMessage);
                messages.add(userMsgMap);

                body.put("messages", messages);
            } else {
                targetUrl = apiUrl + modelName;
                body.put("inputs", userMessage);
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
            if (apiToken != null && !apiToken.trim().isEmpty()) {
                String cleanToken = apiToken.trim();
                if (cleanToken.startsWith("hf_hf_")) {
                    cleanToken = cleanToken.substring(3);
                }
                if (cleanToken.endsWith(".env")) {
                    cleanToken = cleanToken.substring(0, cleanToken.length() - 4);
                }
                headers.set("Authorization", "Bearer " + cleanToken);
            }

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
            ResponseEntity<Object> responseEntity = restTemplate.postForEntity(targetUrl, entity, Object.class);

            if (responseEntity.getStatusCode().is2xxSuccessful() && responseEntity.getBody() != null) {
                Object responseBody = responseEntity.getBody();
                String generatedText = null;

                if (responseBody instanceof Map) {
                    Map<?, ?> map = (Map<?, ?>) responseBody;
                    if (map.containsKey("choices")) {
                        Object choicesObj = map.get("choices");
                        if (choicesObj instanceof List) {
                            List<?> choicesList = (List<?>) choicesObj;
                            if (!choicesList.isEmpty()) {
                                Object firstChoice = choicesList.get(0);
                                if (firstChoice instanceof Map) {
                                    Map<?, ?> choiceMap = (Map<?, ?>) firstChoice;
                                    Object messageObj = choiceMap.get("message");
                                    if (messageObj instanceof Map) {
                                        Map<?, ?> messageMap = (Map<?, ?>) messageObj;
                                        generatedText = (String) messageMap.get("content");
                                    }
                                }
                            }
                        }
                    } else {
                        generatedText = (String) map.get("generated_text");
                    }
                } else if (responseBody instanceof List) {
                    List<?> list = (List<?>) responseBody;
                    if (!list.isEmpty()) {
                        Object first = list.get(0);
                        if (first instanceof Map) {
                            Map<?, ?> map = (Map<?, ?>) first;
                            generatedText = (String) map.get("generated_text");
                            if (generatedText == null) {
                                // Try conversational model response format
                                Object conversationObj = map.get("conversation");
                                if (conversationObj instanceof Map) {
                                    Map<?, ?> convMap = (Map<?, ?>) conversationObj;
                                    Object generatedResponses = convMap.get("generated_responses");
                                    if (generatedResponses instanceof List) {
                                        List<?> genList = (List<?>) generatedResponses;
                                        if (!genList.isEmpty()) {
                                            generatedText = String.valueOf(genList.get(genList.size() - 1));
                                        }
                                    }
                                }
                            }
                        } else {
                            generatedText = String.valueOf(first);
                        }
                    }
                }

                if (generatedText == null || generatedText.trim().isEmpty()) {
                    generatedText = "I received a response, but could not interpret the text representation.";
                } else {
                    // Update session history
                    Map<String, String> histUser = new HashMap<>();
                    histUser.put("role", "user");
                    histUser.put("content", userMessage);
                    state.history.add(histUser);

                    Map<String, String> histAssistant = new HashMap<>();
                    histAssistant.put("role", "assistant");
                    histAssistant.put("content", generatedText);
                    state.history.add(histAssistant);

                    if (state.history.size() > 10) {
                        state.history = new ArrayList<>(state.history.subList(state.history.size() - 10, state.history.size()));
                    }
                }

                Map<String, String> data = new HashMap<>();
                data.put("response", generatedText);
                return ResponseEntity.ok(ApiResponse.success("Success", data));
            } else {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(ApiResponse.error("Failed to get response from AI model"));
            }
        } catch (org.springframework.web.client.HttpStatusCodeException ex) {
            if (ex.getStatusCode().value() == 503) {
                Map<String, String> data = new HashMap<>();
                data.put("response", "The AI model is currently initializing. Please try again in a few seconds.");
                return ResponseEntity.ok(ApiResponse.success("Success", data));
            }
            return ResponseEntity.status(ex.getStatusCode())
                    .body(ApiResponse.error("Error calling AI service: " + ex.getResponseBodyAsString()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Error communicating with AI service: " + e.getMessage()));
        }
    }

    private List<ProductDto> getRelevantProducts(String userMessage, ChatState state, Map<Long, String> categoryMap) {
        List<ProductDto> allProducts = productService.getAllProducts();
        if (allProducts == null || allProducts.isEmpty()) {
            return new ArrayList<>();
        }

        String msgLower = userMessage.toLowerCase();

        // 1. Check for specific product names in user message
        List<ProductDto> matchedByName = new ArrayList<>();
        for (ProductDto p : allProducts) {
            String name = p.getName();
            if (name != null && name.length() > 3 && msgLower.contains(name.toLowerCase())) {
                matchedByName.add(p);
            }
        }
        if (!matchedByName.isEmpty()) {
            return matchedByName.subList(0, Math.min(5, matchedByName.size()));
        }

        // 2. Filter products by the active category if set in the state
        List<ProductDto> filtered = new ArrayList<>();
        if (state.activeCategory != null) {
            String activeCatLower = state.activeCategory.toLowerCase();
            for (ProductDto p : allProducts) {
                String catName = categoryMap.get(p.getCategoryId());
                if (catName != null && catName.toLowerCase().contains(activeCatLower)) {
                    filtered.add(p);
                }
            }
        } else {
            filtered = allProducts;
        }

        // 3. Filter by price limit if set in the state
        if (state.maxPrice != null) {
            List<ProductDto> temp = new ArrayList<>();
            for (ProductDto p : filtered) {
                if (p.getPrice() != null && p.getPrice().doubleValue() <= state.maxPrice) {
                    temp.add(p);
                }
            }
            if (!temp.isEmpty()) {
                filtered = temp;
            }
        }

        // 4. Return top 5 from the filtered list
        if (!filtered.isEmpty()) {
            return filtered.subList(0, Math.min(5, filtered.size()));
        }

        // Fallback to default diverse list
        List<ProductDto> defaultProducts = new ArrayList<>();
        for (ProductDto p : allProducts) {
            String name = p.getName() != null ? p.getName().toLowerCase() : "";
            if (name.contains("titan neo") || name.contains("casio vintage") || name.contains("rado captain") || name.contains("apple watch ultra")) {
                if (!defaultProducts.contains(p)) {
                    defaultProducts.add(p);
                }
            }
        }
        for (ProductDto p : allProducts) {
            if (defaultProducts.size() >= 5) break;
            if (!defaultProducts.contains(p)) {
                defaultProducts.add(p);
            }
        }
        return defaultProducts;
    }
}

