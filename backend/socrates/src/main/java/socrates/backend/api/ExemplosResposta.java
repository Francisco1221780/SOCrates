package socrates.backend.api;

/**
 * Exemplos de resposta para o Swagger UI (OpenAPI): respostas reais do motor Prolog aos pedidos de {@link ExemplosPedido},
 * encurtadas. Os controladores devolvem o JSON do motor como texto, por isso sem estes exemplos o OpenAPI so mostraria
 * "string". Os dois motores respondem no mesmo formato; so os numeros dos factos mudam.
 */
final class ExemplosResposta {

    private ExemplosResposta() {
    }

    /** POST /api/{motor}/infer, 200: o cenario 7 (encurtado: uma pergunta, um incidente, um ticket e um facto de cada origem; a resposta real traz todos). */
    static final String INFER_200 = """
            {
              "estado": "aguarda_resposta",
              "motor": "prolog",
              "versaoKB": "6.1",
              "perguntas": [
                {
                  "facto": 427,
                  "ativo": "web_srv_04",
                  "evento": "ev_e1",
                  "pergunta": "shell_servico",
                  "texto": "O sensor deste servidor esta em baixo. Consegue ver uma shell ativa lancada pelo servico que foi atacado?",
                  "opcoes": [
                    {
                      "valor": "sim",
                      "texto": "Sim, ha uma shell lancada pelo servico"
                    },
                    {
                      "valor": "nao",
                      "texto": "Nao, nao ha nenhuma shell lancada pelo servico"
                    },
                    {
                      "valor": "nao_sei",
                      "texto": "Nao sei, nao consigo verificar"
                    }
                  ]
                }
              ],
              "incidentes": [
                {
                  "facto": 428,
                  "id": {
                    "f": "inc",
                    "args": [
                      "inspecao_manual",
                      "ev_e1"
                    ]
                  },
                  "tipo": "inspecao_manual",
                  "ativo": "web_srv_04",
                  "impactados": [
                    "web_srv_04"
                  ]
                }
              ],
              "tickets": [
                {
                  "facto": 435,
                  "incidente": {
                    "f": "inc",
                    "args": [
                      "inspecao_manual",
                      "ev_e1"
                    ]
                  },
                  "ativo": "web_srv_04",
                  "acao": "inspecao_manual_do_host",
                  "gravidade": "media",
                  "prioridade": "p3",
                  "impactados": [
                    "web_srv_04"
                  ],
                  "fechado": null
                }
              ],
              "factos": [
                {
                  "numero": 1,
                  "predicado": "zona",
                  "args": [
                    "web_srv_01",
                    "dmz"
                  ],
                  "origem": "inicial",
                  "passo": null,
                  "regra": null,
                  "premissas": []
                },
                {
                  "numero": 420,
                  "predicado": "estado_sensor",
                  "args": [
                    "web_srv_04",
                    "agente_host",
                    "offline"
                  ],
                  "origem": "entrada",
                  "passo": "c7",
                  "regra": null,
                  "premissas": []
                },
                {
                  "numero": 391,
                  "predicado": "classe_ativo",
                  "args": [
                    "web_srv_01",
                    "critico"
                  ],
                  "origem": "regra",
                  "passo": null,
                  "regra": 74,
                  "premissas": [
                    17
                  ]
                }
              ],
              "erro": null
            }""";

    /** POST /api/{motor}/como, 200: a arvore da pergunta do cenario 7 (encurtada: so a primeira premissa de cada no). */
    static final String COMO_200 = """
            {
              "estado": "concluido",
              "motor": "prolog",
              "versaoKB": "6.1",
              "facto": {
                "numero": 427,
                "predicado": "pergunta_analista",
                "args": [
                  "web_srv_04",
                  "ev_e1",
                  "shell_servico"
                ],
                "origem": "regra",
                "passo": "c7",
                "regra": 31,
                "premissas": [
                  426,
                  {
                    "f": "nao",
                    "args": [
                      {
                        "f": "impacto_excluido",
                        "args": [
                          "ev_e1",
                          "web_srv_04",
                          "cve_2021_41773",
                          "execucao_remota",
                          "A"
                        ]
                      }
                    ]
                  },
                  420,
                  188,
                  202,
                  {
                    "f": "nao",
                    "args": [
                      {
                        "f": "resposta_analista",
                        "args": [
                          "web_srv_04",
                          "ev_e1",
                          "shell_servico",
                          "B"
                        ]
                      }
                    ]
                  }
                ]
              },
              "arvore": {
                "numero": 427,
                "predicado": "pergunta_analista",
                "args": [
                  "web_srv_04",
                  "ev_e1",
                  "shell_servico"
                ],
                "regra": 31,
                "jaExplicado": false,
                "premissas": [
                  {
                    "tipo": "facto",
                    "no": {
                      "numero": 426,
                      "predicado": "impacto_possivel",
                      "args": [
                        "ev_e1",
                        "web_srv_04",
                        "cve_2021_41773",
                        "execucao_remota"
                      ],
                      "regra": 17,
                      "jaExplicado": false,
                      "premissas": [
                        {
                          "tipo": "facto",
                          "no": {
                            "numero": 425,
                            "predicado": "vetor_aberto",
                            "args": [
                              "ev_e1",
                              "web_srv_04",
                              "web_srv_04_apache",
                              "cve_2021_41773"
                            ],
                            "regra": 4,
                            "jaExplicado": false,
                            "premissas": []
                          }
                        }
                      ]
                    }
                  }
                ]
              },
              "erro": null
            }""";

    /** POST /api/{motor}/porque-pergunta, 200 (encurtado: uma opcao e uma premissa). */
    static final String PORQUE_PERGUNTA_200 = """
            {
              "estado": "concluido",
              "motor": "prolog",
              "versaoKB": "6.1",
              "pergunta": {
                "facto": 427,
                "ativo": "web_srv_04",
                "evento": "ev_e1",
                "pergunta": "shell_servico",
                "texto": "O sensor deste servidor esta em baixo. Consegue ver uma shell ativa lancada pelo servico que foi atacado?",
                "opcoes": [
                  {
                    "valor": "sim",
                    "texto": "Sim, ha uma shell lancada pelo servico"
                  }
                ]
              },
              "geradaPor": {
                "numero": 427,
                "predicado": "pergunta_analista",
                "args": [
                  "web_srv_04",
                  "ev_e1",
                  "shell_servico"
                ],
                "regra": 31,
                "jaExplicado": false,
                "regraCompleta": {
                  "id": 31,
                  "se": [
                    {
                      "f": "impacto_possivel",
                      "args": [
                        {
                          "var": "Ev"
                        },
                        {
                          "var": "Alvo"
                        },
                        {
                          "var": "Vuln"
                        },
                        {
                          "var": "Impacto"
                        }
                      ]
                    },
                    {
                      "nao": {
                        "f": "impacto_excluido",
                        "args": [
                          {
                            "var": "Ev"
                          },
                          {
                            "var": "Alvo"
                          },
                          {
                            "var": "Vuln"
                          },
                          {
                            "var": "Impacto"
                          },
                          {
                            "var": "_"
                          }
                        ]
                      }
                    },
                    {
                      "f": "estado_sensor",
                      "args": [
                        {
                          "var": "Alvo"
                        },
                        "agente_host",
                        "offline"
                      ]
                    },
                    {
                      "f": "evidencia_impacto",
                      "args": [
                        {
                          "var": "Impacto"
                        },
                        {
                          "var": "Tipo"
                        },
                        {
                          "var": "_"
                        }
                      ]
                    },
                    {
                      "f": "pergunta_evidencia",
                      "args": [
                        {
                          "var": "Tipo"
                        },
                        {
                          "var": "Pergunta"
                        }
                      ]
                    },
                    {
                      "nao": {
                        "f": "resposta_analista",
                        "args": [
                          {
                            "var": "Alvo"
                          },
                          {
                            "var": "Ev"
                          },
                          {
                            "var": "Pergunta"
                          },
                          {
                            "var": "_"
                          }
                        ]
                      }
                    }
                  ],
                  "entao": [
                    {
                      "f": "pergunta_analista",
                      "args": [
                        {
                          "var": "Alvo"
                        },
                        {
                          "var": "Ev"
                        },
                        {
                          "var": "Pergunta"
                        }
                      ]
                    },
                    {
                      "f": "incidente",
                      "args": [
                        {
                          "f": "inc",
                          "args": [
                            "inspecao_manual",
                            {
                              "var": "Ev"
                            }
                          ]
                        },
                        "inspecao_manual",
                        {
                          "var": "Alvo"
                        },
                        [
                          {
                            "var": "Alvo"
                          }
                        ]
                      ]
                    }
                  ]
                },
                "premissas": [
                  {
                    "tipo": "facto",
                    "no": {
                      "numero": 426,
                      "predicado": "impacto_possivel",
                      "args": [
                        "ev_e1",
                        "web_srv_04",
                        "cve_2021_41773",
                        "execucao_remota"
                      ],
                      "regra": 17,
                      "jaExplicado": false,
                      "premissas": []
                    }
                  }
                ]
              },
              "regrasQueUsamAResposta": [
                32,
                33,
                34,
                50,
                51,
                94
              ],
              "erro": null
            }""";

    /** POST /api/{motor}/whynot, 200 (encurtado: as duas primeiras linhas da explicacao). */
    static final String WHYNOT_200 = """
            {
              "estado": "concluido",
              "motor": "prolog",
              "versaoKB": "6.1",
              "facto": {
                "predicado": "compromisso_confirmado",
                "args": [
                  "web_srv_04",
                  {
                    "var": "Ev"
                  }
                ]
              },
              "explicacao": [
                {
                  "nivel": 1,
                  "tipo": "regra",
                  "regra": 25,
                  "se": [
                    {
                      "tipo": "facto",
                      "termo": {
                        "f": "impacto_confirmado",
                        "args": [
                          "web_srv_04",
                          "A",
                          "B"
                        ]
                      },
                      "verificada": false
                    },
                    {
                      "tipo": "facto",
                      "termo": {
                        "f": "classe_impacto",
                        "args": [
                          "A",
                          "controlo"
                        ]
                      },
                      "verificada": true,
                      "numero": 184
                    }
                  ],
                  "entao": [
                    {
                      "f": "compromisso_confirmado",
                      "args": [
                        "web_srv_04",
                        "A"
                      ]
                    }
                  ]
                },
                {
                  "nivel": 2,
                  "tipo": "premissa_falsa",
                  "termo": {
                    "f": "impacto_confirmado",
                    "args": [
                      "web_srv_04",
                      "A",
                      "B"
                    ]
                  }
                }
              ],
              "erro": null
            }""";

    /** Pedido invalido (400): aqui, a versaoKB nao e a da base carregada. */
    static final String ERRO_400 = """
            {
              "estado": "erro",
              "motor": "prolog",
              "versaoKB": "6.1",
              "erro": "versaoKB desconhecida: 6 (carregada: 6.1)"
            }""";

    /** Motor desconhecido (404). */
    static final String ERRO_404 = """
            {
              "estado": "erro",
              "motor": "outro",
              "versaoKB": "6.1",
              "erro": "motor desconhecido: outro (disponiveis: drools, prolog)"
            }""";

    /** O facto ou a pergunta pedidos nao estao na resposta do motor (422). */
    static final String ERRO_422 = """
            {
              "estado": "erro",
              "motor": "prolog",
              "versaoKB": "6.1",
              "erro": "esse facto nao esta na memoria de trabalho (experimente o porque nao)"
            }""";

    /** Todos os processos do motor ocupados (503). */
    static final String ERRO_503 = """
            {
              "estado": "erro",
              "motor": "prolog",
              "versaoKB": null,
              "erro": "ocupado: 4 pedidos prolog em curso"
            }""";

    /** O motor nao respondeu dentro do timeout (504). */
    static final String ERRO_504 = """
            {
              "estado": "erro",
              "motor": "prolog",
              "versaoKB": null,
              "erro": "timeout"
            }""";

    /** O motor falhou (500): aqui, o swipl nao arrancou (a mensagem depende da falha). */
    static final String ERRO_500 = """
            {
              "estado": "erro",
              "motor": "prolog",
              "versaoKB": null,
              "erro": "swipl nao encontrado ou falhou ao iniciar: Cannot run program \\"swipl\\""
            }""";

    /** O Content-Type nao e application/json (415): a resposta e a do Spring, nao a do motor. */
    static final String ERRO_415 = """
            {
              "timestamp": "2026-10-22T09:53:11.042Z",
              "status": 415,
              "error": "Unsupported Media Type",
              "path": "/api/prolog/infer"
            }""";

}
