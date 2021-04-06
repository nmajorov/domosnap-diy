# Domosnap-probe-project

## Prerequis

*  maven 3
*  OpenJDK 13
 
## Run

### On your laptop
	mvn clean package
	cd target
	java -jar probe-project-0.x.x-SNAPSHOT.jar -conf ../src/main/configuration/default-configuration.json
    

### On raspberry cluster

    mvn clean package
    sudo docker build -f Dockerfile_arm32v6 . -t adegiuli/probe-project-arm32v6:latest
    sudo docker push adegiuli/probe-project-arm32v6:latest
    kubectl delete configmap probe-project
    kubectl create configmap probe-project --from-file=config.json=./src/main/configuration/default-configuration.json
    kubectl apply -f probe-project-manual.yaml
    

### On pipeline

To be done

### Remark
For new project don't forget to register the token:
    - Create key in gitlab (Project->Settings->Repository->Deploy Tokens with a username and password)
    - On k8s cluster add the key:

    kubectl create secret docker-registry customer-registry-secret --docker-server=registry.gitlab.com --docker-username='gitlab-deploy-token' --docker-password='generated-token'

[More info](https://kubernetes.io/fr/docs/tasks/configure-pod-container/pull-image-private-registry/ "More info")


Don't forget to use imagePullSecret in pod definition: 
    
    imagePullSecrets:
        - name: customer-registry-secret