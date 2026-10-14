package com.gabrielpacheco.atendente.adapter.out.nlu.claude;

import com.anthropic.client.AnthropicClient;
import com.anthropic.errors.AnthropicException;
import com.anthropic.errors.AnthropicInvalidDataException;
import com.anthropic.errors.AnthropicIoException;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.errors.RateLimitException;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.RefusalStopDetails;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.StructuredMessage;
import com.anthropic.models.messages.StructuredMessageCreateParams;
import com.anthropic.models.messages.StructuredTextBlock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Chama a Messages API com saída estruturada e effort baixo. Não envia temperature nem thinking, que o modelo recusa.
 * O log traz só a contagem de tokens e o motivo de uma recusa, nunca o conteúdo.
 */
public final class AnthropicClaudeGateway implements ClaudeGateway {

    private static final Logger log = LoggerFactory.getLogger(AnthropicClaudeGateway.class);

    private final AnthropicClient client;
    private final String model;
    private final long maxTokens;
    private final OutputConfig outputConfig;

    public AnthropicClaudeGateway(AnthropicClient client, String model, String effort, long maxTokens) {
        this.client = client;
        this.model = model;
        this.maxTokens = maxTokens;
        this.outputConfig = schemaWithEffort(model, OutputConfig.Effort.of(effort));
    }

    @Override
    public ClaudeResult extract(String system, String user) {
        try {
            return read(client.messages().create(params(system, user)));
        } catch (RateLimitException e) {
            return new ClaudeResult.Failed("rate_limit", e);
        } catch (AnthropicServiceException e) {
            return new ClaudeResult.Failed("http_" + e.statusCode(), e);
        } catch (AnthropicIoException e) {
            return new ClaudeResult.Failed("io", e);
        } catch (AnthropicInvalidDataException e) {
            return new ClaudeResult.Failed("saida_invalida", e);
        } catch (AnthropicException e) {
            return new ClaudeResult.Failed("sdk", e);
        }
    }

    /**
     * O primeiro {@code outputConfig} tipa a resposta; o segundo o substitui pelo mesmo formato com o effort, porque
     * chamar {@code outputConfig(Class)} não deixa escolher o effort.
     */
    StructuredMessageCreateParams<ClaudeExtraction> params(String system, String user) {
        return MessageCreateParams.builder()
                .model(model)
                .maxTokens(maxTokens)
                .system(system)
                .addUserMessage(user)
                .outputConfig(ClaudeExtraction.class)
                .outputConfig(outputConfig)
                .build();
    }

    /** O esquema JSON que o SDK gera a partir de {@link ClaudeExtraction}, com o effort acrescentado. */
    private static OutputConfig schemaWithEffort(String model, OutputConfig.Effort effort) {
        OutputConfig schemaOnly = MessageCreateParams.builder()
                .model(model)
                .maxTokens(1)
                .addUserMessage("x")
                .outputConfig(ClaudeExtraction.class)
                .build()
                .rawParams()
                .outputConfig()
                .orElseThrow();
        return schemaOnly.toBuilder().effort(effort).build();
    }

    private static ClaudeResult read(StructuredMessage<ClaudeExtraction> response) {
        log.info("Claude usou {} tokens de entrada e {} de saída",
                response.usage().inputTokens(), response.usage().outputTokens());
        StopReason stop = response.stopReason().orElse(null);
        if (!StopReason.END_TURN.equals(stop)) {
            if (StopReason.REFUSAL.equals(stop)) {
                log.info("Claude recusou a mensagem (categoria {})", response.stopDetails()
                        .flatMap(RefusalStopDetails::category)
                        .map(RefusalStopDetails.Category::asString)
                        .orElse("não informada"));
            }
            return new ClaudeResult.NotUsable(stop == null ? "sem_stop_reason" : stop.asString());
        }
        return response.content().stream()
                .flatMap(block -> block.text().stream())
                .findFirst()
                .map(StructuredTextBlock::text)
                .<ClaudeResult>map(ClaudeResult.Success::new)
                .orElseGet(() -> new ClaudeResult.NotUsable("sem_conteudo"));
    }
}
