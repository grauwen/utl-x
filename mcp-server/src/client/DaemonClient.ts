/**
 * HTTP client for communicating with UTL-X Daemon REST API
 */

import axios, { AxiosInstance, AxiosError } from 'axios';
import FormData from 'form-data';
import { Logger } from 'winston';
import {
  ValidationRequest,
  ValidationResponse,
  ExecutionRequest,
  ExecutionResponse,
  InferSchemaRequest,
  InferSchemaResponse,
  ParseSchemaRequest,
  ParseSchemaResponse,
  HealthResponse,
  FunctionRegistry,
} from '../types/daemon';
import { OperatorRegistry } from '../types/operator';
import { DirectiveRegistry } from '../types/usdl';

export interface DaemonClientConfig {
  baseUrl: string;
  timeout?: number;
  retries?: number;
  retryDelay?: number;
}

export class DaemonClient {
  private client: AxiosInstance;
  private logger: Logger;
  private config: Required<DaemonClientConfig>;

  constructor(config: DaemonClientConfig, logger: Logger) {
    this.config = {
      baseUrl: config.baseUrl,
      timeout: config.timeout || 30000,
      retries: config.retries || 3,
      retryDelay: config.retryDelay || 1000,
    };

    this.logger = logger;

    this.client = axios.create({
      baseURL: this.config.baseUrl,
      timeout: this.config.timeout,
      headers: {
        'Content-Type': 'application/json',
      },
    });

    // Add response interceptor for logging
    this.client.interceptors.response.use(
      (response) => {
        this.logger.debug('Daemon API response', {
          url: response.config.url,
          status: response.status,
        });
        return response;
      },
      async (error: AxiosError) => {
        this.logger.error('Daemon API error', {
          url: error.config?.url,
          status: error.response?.status,
          message: error.message,
        });
        throw error;
      }
    );
  }

  /**
   * Check daemon health
   */
  async health(): Promise<HealthResponse> {
    return this.withRetry(async () => {
      const response = await this.client.get<HealthResponse>('/api/health');
      return response.data;
    });
  }

  /**
   * Validate UTLX code
   */
  async validate(request: ValidationRequest): Promise<ValidationResponse> {
    return this.withRetry(async () => {
      const response = await this.client.post<ValidationResponse>('/api/validate', request);
      return response.data;
    });
  }

  /**
   * Execute transformation
   */
  async execute(request: ExecutionRequest): Promise<ExecutionResponse> {
    return this.withRetry(async () => {
      const response = await this.client.post<ExecutionResponse>('/api/execute', request);
      return response.data;
    });
  }

  /**
   * Execute with N named inputs via the multipart endpoint (IB02). Each input is a
   * file part whose filename = the input's declared name and `X-Format` = its format,
   * so the daemon binds `$name` correctly (and supports multiple inputs). The output
   * format comes from the program header. Mirrors the IDE's node daemon-client.
   */
  async executeMultipart(
    utlx: string,
    inputs: Array<{ name: string; format: string; content: string }>
  ): Promise<ExecutionResponse> {
    return this.withRetry(async () => {
      const form = new FormData();
      form.append('utlx', utlx);
      inputs.forEach((inp, i) => {
        const buffer = Buffer.from(inp.content, 'utf-8');
        form.append(`input_${i}`, buffer, {
          filename: inp.name,
          contentType: 'application/octet-stream',
          knownLength: buffer.length,
          header: {
            'X-Format': inp.format,
            'X-Encoding': 'UTF-8',
            'X-BOM': 'false',
          },
        });
      });
      const response = await this.client.post<ExecutionResponse>(
        '/api/execute-multipart',
        form,
        {
          headers: form.getHeaders(),
          maxBodyLength: Infinity,
          maxContentLength: Infinity,
        }
      );
      return response.data;
    });
  }

  /**
   * Infer output schema
   */
  async inferSchema(request: InferSchemaRequest): Promise<InferSchemaResponse> {
    return this.withRetry(async () => {
      const response = await this.client.post<InferSchemaResponse>('/api/infer-schema', request);
      return response.data;
    });
  }

  /**
   * Parse schema
   */
  async parseSchema(request: ParseSchemaRequest): Promise<ParseSchemaResponse> {
    return this.withRetry(async () => {
      const response = await this.client.post<ParseSchemaResponse>('/api/parse-schema', request);
      return response.data;
    });
  }

  /**
   * Get standard library functions
   */
  async getFunctions(): Promise<FunctionRegistry> {
    return this.withRetry(async () => {
      const response = await this.client.get<FunctionRegistry>('/api/functions');
      return response.data;
    });
  }

  /**
   * Get operator registry
   */
  async getOperators(): Promise<OperatorRegistry> {
    return this.withRetry(async () => {
      const response = await this.client.get<OperatorRegistry>('/api/operators');
      return response.data;
    });
  }

  /**
   * Get USDL directive registry
   */
  async getUsdlDirectives(): Promise<DirectiveRegistry> {
    return this.withRetry(async () => {
      const response = await this.client.get<DirectiveRegistry>('/api/usdl/directives');
      return response.data;
    });
  }

  /**
   * Retry wrapper with exponential backoff
   */
  private async withRetry<T>(fn: () => Promise<T>): Promise<T> {
    let lastError: Error | undefined;

    for (let attempt = 0; attempt < this.config.retries; attempt++) {
      try {
        return await fn();
      } catch (error) {
        lastError = error as Error;

        // Don't retry on 4xx errors (client errors)
        if (axios.isAxiosError(error) && error.response && error.response.status < 500) {
          throw error;
        }

        if (attempt < this.config.retries - 1) {
          const delay = this.config.retryDelay * Math.pow(2, attempt);
          this.logger.warn(`Retrying after ${delay}ms (attempt ${attempt + 1}/${this.config.retries})`);
          await this.sleep(delay);
        }
      }
    }

    throw lastError;
  }

  private sleep(ms: number): Promise<void> {
    return new Promise((resolve) => setTimeout(resolve, ms));
  }
}
