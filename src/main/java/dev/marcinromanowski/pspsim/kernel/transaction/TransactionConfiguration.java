package dev.marcinromanowski.pspsim.kernel.transaction;

import dev.marcinromanowski.pspsim.kernel.transaction.internal.TransactionTemplateUnitOfWork;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration(proxyBeanMethods = false)
public class TransactionConfiguration {

  @Bean
  public TransactionTemplateUnitOfWork unitOfWork(
      PlatformTransactionManager transactionManager, List<ClaimConstraints> claims) {
    return new TransactionTemplateUnitOfWork(new TransactionTemplate(transactionManager), claims);
  }
}
