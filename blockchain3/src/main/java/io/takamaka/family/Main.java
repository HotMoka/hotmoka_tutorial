package io.takamaka.family;

import static java.math.BigInteger.ONE;

import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyPair;
import java.util.Base64;

import io.hotmoka.beans.references.TransactionReference;
import io.hotmoka.beans.requests.ConstructorCallTransactionRequest;
import io.hotmoka.beans.requests.InstanceMethodCallTransactionRequest;
import io.hotmoka.beans.requests.JarStoreTransactionRequest;
import io.hotmoka.beans.requests.SignedTransactionRequest;
import io.hotmoka.beans.requests.SignedTransactionRequest.Signer;
import io.hotmoka.beans.signatures.CodeSignature;
import io.hotmoka.beans.signatures.ConstructorSignature;
import io.hotmoka.beans.types.ClassType;
import io.hotmoka.beans.values.BigIntegerValue;
import io.hotmoka.beans.values.StorageReference;
import io.hotmoka.beans.values.StringValue;
import io.hotmoka.crypto.SignatureAlgorithm;
import io.hotmoka.memory.MemoryBlockchain;
import io.hotmoka.memory.MemoryBlockchainConfig;
import io.hotmoka.nodes.ConsensusParams;
import io.hotmoka.nodes.GasHelper;
import io.hotmoka.nodes.Node;
import io.hotmoka.nodes.views.InitializedNode;

/**
 * Go inside the hotmoka project, run
 * 
 * . set_variables.sh
 * 
 * then move inside this project and run
 * 
 * mvn clean package
 * java --module-path $explicit:$automatic:target/blockchain-0.0.1-SNAPSHOT.jar -classpath $unnamed"/*" --module blockchain/io.takamaka.family.Main
 */
public class Main {
  public final static BigInteger GREEN_AMOUNT = BigInteger.valueOf(100_000_000);
  public final static BigInteger RED_AMOUNT = BigInteger.ZERO;

  public static void main(String[] args) throws Exception {
    MemoryBlockchainConfig config = new MemoryBlockchainConfig.Builder().build();
    ConsensusParams consensus = new ConsensusParams.Builder().build();

    // the path of the packaged runtime Takamaka classes
    Path takamakaCodePath = Paths.get
      ("../../hotmoka/modules/explicit/io-takamaka-code-1.0.0.jar");

    // the path of the user jar to install
    Path familyPath = Paths.get("../family/target/family-0.0.1-SNAPSHOT.jar");

    try (Node node = MemoryBlockchain.init(config, consensus)) {
      // we store io-takamaka-code-1.0.0.jar and create the manifest and the gamete
      InitializedNode initialized = InitializedNode.of
        (node, consensus, takamakaCodePath, GREEN_AMOUNT, RED_AMOUNT);

      // we get a reference to where io-takamaka-code-1.0.0.jar has been stored
      TransactionReference takamakaCode = node.getTakamakaCode();

      // we get a reference to the gamete
      StorageReference gamete = initialized.gamete();

      // we get the signing algorithm to use for requests
      SignatureAlgorithm<SignedTransactionRequest> signature
        = node.getSignatureAlgorithmForRequests();

      // we create a signer that signs with the private key of the gamete
      Signer signerOnBehalfOfGamete = Signer.with
        (signature, initialized.keysOfGamete().getPrivate());

      // we get the nonce of the gamete: we use the gamete as caller and
      // an arbitrary nonce (ZERO in the code) since we are running
      // a @View method of the gamete
      BigInteger nonce = ((BigIntegerValue) node
        .runInstanceMethodCallTransaction(new InstanceMethodCallTransactionRequest
          (gamete, // payer
          BigInteger.valueOf(10_000), // gas limit
          takamakaCode, // class path for the execution of the transaction
          CodeSignature.NONCE, // method
          gamete))) // receiver of the method call
        .value;

      GasHelper gasHelper = new GasHelper(node);

      // we install family-0.0.1-SNAPSHOT.jar in blockchain: the gamete will pay
      TransactionReference family = node
        .addJarStoreTransaction(new JarStoreTransactionRequest
          (signerOnBehalfOfGamete, // an object that signs with the payer's private key
          gamete, // payer
          nonce, // payer's nonce: relevant since this is not a call to a @View method!
          "", // chain identifier: relevant since this is not a call to a @View method!
          BigInteger.valueOf(10_000), // gas limit: enough for this very small jar
          gasHelper.getSafeGasPrice(), // gas price: at least the current gas price of the network
          takamakaCode, // class path for the execution of the transaction
          Files.readAllBytes(familyPath), // bytes of the jar to install
          takamakaCode)); // dependencies of the jar that is being installed

      // we increase to nonce, ready for further transactions having the gamete as payer
      nonce = nonce.add(ONE);

      // create a new public/private key pair to control the new account
      KeyPair keys = signature.getKeyPair();

      // transform the public key in string, Base64 encoded
      String publicKey = Base64.getEncoder().encodeToString
        (keys.getPublic().getEncoded());

   	  // call constructor io.takamaka.code.lang.ExternallyOwnedAccount
      // with arguments (BigInteger funds, String publicKey)
      StorageReference account = node
        .addConstructorCallTransaction(new ConstructorCallTransactionRequest
          (signerOnBehalfOfGamete, // an object that signs with the payer's private key
           gamete, // payer
           nonce, // nonce of the payer, relevant
           "", // chain identifier, relevant
           BigInteger.valueOf(10_000), // gas limit: enough for the creation of an account
           gasHelper.getSafeGasPrice(), // gas price
           takamakaCode, // class path for the execution of the transaction

           // signature of the constructor to call
           new ConstructorSignature("io.takamaka.code.lang.ExternallyOwnedAccount",
             ClassType.BIG_INTEGER, ClassType.STRING),

           // actual arguments passed to the constructor:
           // we fund it with 100,000 units of green coin
           new BigIntegerValue(BigInteger.valueOf(100_000)), new StringValue(publicKey)));

      System.out.println("manifest: " + node.getManifest());
      System.out.println("gamete: " + gamete);
      System.out.println("nonce of gamete: " + nonce);
      System.out.println("family-0.0.1-SNAPSHOT.jar: " + family);
      System.out.println("account: " + account);

      // we increase to nonce, ready for further transactions having the gamete as payer
      nonce = nonce.add(ONE);
    }
  }
}