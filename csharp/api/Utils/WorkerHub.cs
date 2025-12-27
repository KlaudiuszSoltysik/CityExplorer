using csharp.Dtos;
using Microsoft.AspNetCore.SignalR;

namespace csharp.Utils;

public interface IWorkerClient
{
    Task JobCompleted(WorkerResult result);
    Task JobFailed(string reason);
}

public class WorkerHub : Hub<IWorkerClient>
{
    public async Task JoinJobGroup(string jobId)
    {
        await Groups.AddToGroupAsync(Context.ConnectionId, jobId);
    }
}